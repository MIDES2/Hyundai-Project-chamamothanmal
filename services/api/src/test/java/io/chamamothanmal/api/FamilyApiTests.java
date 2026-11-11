package io.chamamothanmal.api;

import com.fasterxml.jackson.databind.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FamilyApiTests {
    static final String S="Bearer test_sender_token_0000000000000000000001";
    static final String R="Bearer test_recipient_token_000000000000000001";
    static final String O="Bearer test_other_token_000000000000000000001";
    static final Path DIR=Path.of("build","v2-test-uploads-"+UUID.randomUUID()).toAbsolutePath();
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
        r.add("app.upload-dir",DIR::toString);
        r.add("spring.datasource.url",()->"jdbc:h2:mem:family-v2-tests;DB_CLOSE_DELAY=-1");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired FamilyService service;
    String family,vehicle,deviceToken;
    @BeforeEach void reset() throws Exception {
        for(String table:List.of("cm_friend_messages","cm_friends","cm_auth_deletions","cm_replies","cm_events","cm_deliveries","cm_sessions","cm_messages","cm_devices","cm_codes","cm_vehicles","cm_members","cm_families","cm_users")) db.update("DELETE FROM "+table);
        Files.createDirectories(DIR);
        try(var files=Files.list(DIR)) { for(Path f:files.toList()) Files.delete(f); }
    }
    String uuid() { return UUID.randomUUID().toString(); }
    JsonNode body(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsByteArray()); }
    MockHttpServletRequestBuilder req(String path,String auth,Object data) throws Exception {
        return post("/api/v2"+path).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(data));
    }
    JsonNode send(String path,String auth,Object data,int status) throws Exception {
        return body(mvc.perform(req(path,auth,data)).andExpect(status().is(status)));
    }
    JsonNode carSend(String path,Object data,int status) throws Exception {
        return body(mvc.perform(req(path,R,data).header("X-Vehicle-Token",deviceToken)).andExpect(status().is(status)));
    }
    void setup() throws Exception {
        family=send("/families",S,Map.of("name","Test family"),201).get("familyId").asText();
        String code=send("/families/"+family+"/invitations",S,Map.of(),200).get("code").asText();
        send("/families/join",R,Map.of("code",code),200);
        vehicle=send("/vehicles",S,Map.of("familyId",family,"name","Test car"),201).get("vehicleId").asText();
        deviceToken=pairCode();
    }
    String pairCode() throws Exception {
        String code=send("/vehicles/"+vehicle+"/pairing-codes",S,Map.of(),200).get("code").asText();
        return send("/vehicles/pair",R,Map.of("code",code),200).get("vehicleToken").asText();
    }
    byte[] wav() {
        var b=ByteBuffer.allocate(32044).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(32036).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)1);
        b.putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(32000);
        return b.array();
    }
    ResultActions upload(String auth,String key) throws Exception {
        return mvc.perform(multipart("/api/v2/messages").file(new MockMultipartFile("audio","test.wav","audio/wav",wav()))
            .header("Authorization",auth).header("Idempotency-Key",key).param("vehicleId",vehicle)
            .param("placeName","Synthetic place").param("latitude","37").param("longitude","127"));
    }
    String message() throws Exception { return body(upload(S,uuid()).andExpect(status().isCreated())).get("messageId").asText(); }
    Map<String,Object> startBody(String mid) {
        return new HashMap<>(Map.of("requestId",uuid(),"consent",true,"parkedConfirmed",true,
            "destinationLatitude",37,"destinationLongitude",127,"source","MOCK","messageIds",List.of(mid)));
    }
    String start(String mid) throws Exception { return carSend("/vehicles/"+vehicle+"/sessions",startBody(mid),200).get("sessionId").asText(); }
    String path(String sid,String mid) { return "/sessions/"+sid+"/messages/"+mid; }
    Map<String,Object> event(String status,long version,String id) { return Map.of("eventId",id,"status",status,"expectedVersion",version,"source","MOCK","reason","USER_ACTION"); }
    @Test void friendMessagesKeepAudioPrivateAndSeparateDownloadFromCompletion() throws Exception {
        mvc.perform(get("/api/v2/me").header("Authorization",R)).andExpect(status().isOk());
        mvc.perform(put("/api/v2/friends").header("Authorization",S).contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":\"demo-recipient\"}")).andExpect(status().isOk());
        String base="/api/v2/friend-messages";
        String requestId=uuid();
        String message="""
            {"recipientId":"demo-recipient","placeName":"Test pin","latitude":37,
             "longitude":127,"listeningMode":"PRIVATE"}
            """;
        var metadata=new MockMultipartFile("message","", "application/json",message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var audio=new MockMultipartFile("audio","test.wav","audio/wav",wav());
        var first=body(mvc.perform(multipart(base).file(metadata).file(audio)
            .header("Authorization",S).header("Idempotency-Key",requestId)).andExpect(status().isOk()));
        String id=first.get("id").asText();
        mvc.perform(multipart(base).file(metadata).file(audio).header("Authorization",S)
            .header("Idempotency-Key",requestId)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get(base+"/"+id+"/audio").header("Authorization",O)).andExpect(status().isNotFound());
        mvc.perform(get(base+"/"+id+"/audio").header("Authorization",R))
            .andExpect(status().isOk()).andExpect(content().bytes(wav()));
        mvc.perform(get(base+"/inbox").header("Authorization",R))
            .andExpect(jsonPath("$[0].id").value(id)).andExpect(jsonPath("$[0].completedAt").isEmpty());
        mvc.perform(put(base+"/"+id+"/completion").header("Authorization",R))
            .andExpect(status().isOk()).andExpect(jsonPath("$.completedAt").isNumber());
        mvc.perform(get(base+"/sent").header("Authorization",S))
            .andExpect(jsonPath("$[0].completedAt").isNumber());
        mvc.perform(get(base+"/archive").header("Authorization",R))
            .andExpect(jsonPath("$[0].id").value(id));
        String declined=body(mvc.perform(multipart(base).file(metadata).file(audio)
            .header("Authorization",S).header("Idempotency-Key",uuid())).andExpect(status().isOk())).get("id").asText();
        mvc.perform(delete(base+"/"+declined).header("Authorization",R)).andExpect(status().isNoContent());
        mvc.perform(get(base+"/"+declined+"/audio").header("Authorization",R)).andExpect(status().isNotFound());
        mvc.perform(get(base+"/inbox").header("Authorization",R)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(base+"/archive").header("Authorization",R)).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void fullFamilyVehicleSessionReplyFlow() throws Exception {
        setup(); String mid=message(), sid=start(mid);
        mvc.perform(get("/api/v2"+path(sid,mid)).header("Authorization",R).header("X-Vehicle-Token",deviceToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.playback.status").value("PENDING"));
        mvc.perform(get("/api/v2"+path(sid,mid)+"/audio").header("Authorization",R).header("X-Vehicle-Token",deviceToken))
            .andExpect(status().isOk()).andExpect(content().bytes(wav()));
        carSend(path(sid,mid)+"/events",event("HELD",0,uuid()),200);
        carSend(path(sid,mid)+"/events",event("PLAYING",1,uuid()),200);
        var complete=event("COMPLETED",2,uuid());
        assertThat(carSend(path(sid,mid)+"/events",complete,200).get("version").asInt()).isEqualTo(3);
        assertThat(carSend(path(sid,mid)+"/events",complete,200).get("version").asInt()).isEqualTo(3);
        var reply=Map.of("replyId",uuid(),"kind","THANK_YOU","parkedConfirmed",true);
        carSend(path(sid,mid)+"/replies",reply,200); carSend(path(sid,mid)+"/replies",reply,200);
        var detail=body(mvc.perform(get("/api/v2/messages/"+mid).header("Authorization",S)).andExpect(status().isOk()));
        assertThat(detail.get("completedAt").isNumber()).isTrue();
        assertThat(detail.get("replies").get(0).get("kind").asText()).isEqualTo("THANK_YOU");
        carSend("/vehicles/"+vehicle+"/sessions",startBody(mid),409);
    }
    @Test void selectedAddressIsSavedAndReturnedWithoutBreakingOldRequests() throws Exception {
        setup();String key=uuid();
        var request=multipart("/api/v2/messages").file(new MockMultipartFile("audio","test.wav","audio/wav",wav()))
            .header("Authorization",S).header("Idempotency-Key",key).param("vehicleId",vehicle)
            .param("placeName","가상대학교 정문").param("address","시험시 가상로 1").param("latitude","37").param("longitude","127");
        String mid=body(mvc.perform(request).andExpect(status().isCreated())).get("messageId").asText();
        mvc.perform(get("/api/v2/messages/"+mid).header("Authorization",S)).andExpect(status().isOk())
            .andExpect(jsonPath("$.address").value("시험시 가상로 1"));
        message(); // previous clients may omit the address
    }
    @Test void codesAreSingleUseAndExpire() throws Exception {
        setup(); String code=send("/families/"+family+"/invitations",S,Map.of(),200).get("code").asText();
        send("/families/join",O,Map.of("code",code),200);
        send("/families/join",O,Map.of("code",code),409);
        code=send("/families/"+family+"/invitations",S,Map.of(),200).get("code").asText();
        db.update("UPDATE cm_codes SET expires_at=0 WHERE consumed=FALSE");
        send("/families/join",O,Map.of("code",code),409);
    }
    @Test void outsiderAndWrongVehicleCredentialCannotRead() throws Exception {
        setup(); String mid=message(),sid=start(mid);
        upload(O,uuid()).andExpect(status().isNotFound());
        mvc.perform(get("/api/v2/vehicles/"+vehicle+"/messages").header("Authorization",R)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v2"+path(sid,mid)+"/audio").header("Authorization",O).header("X-Vehicle-Token",deviceToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v2/messages/"+mid).header("Authorization",R)).andExpect(status().isNotFound());
    }
    @Test void senderPermissionCanBeRevoked() throws Exception {
        setup(); String oldInvite=send("/families/"+family+"/invitations",S,Map.of(),200).get("code").asText();
        mvc.perform(patch("/api/v2/families/"+family+"/members/demo-recipient").header("Authorization",S)
            .contentType(MediaType.APPLICATION_JSON).content("{\"canSend\":false}")).andExpect(status().isNoContent());
        send("/families/join",R,Map.of("code",oldInvite),200);
        upload(R,uuid()).andExpect(status().isForbidden());
    }
    @Test void noConsentOrNoParkingOrDestinationMismatchDoesNotStart() throws Exception {
        setup(); String mid=message(); var body=startBody(mid);
        body.put("consent",false);carSend("/vehicles/"+vehicle+"/sessions",body,409);
        body.put("consent",true); body.put("parkedConfirmed",false);carSend("/vehicles/"+vehicle+"/sessions",body,409);
        body.put("parkedConfirmed",true);body.put("destinationLatitude",38);carSend("/vehicles/"+vehicle+"/sessions",body,409);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM cm_sessions",Integer.class)).isZero();
    }
    @Test void sessionStartAndUploadAreIdempotent() throws Exception {
        setup();String key=uuid();String mid=body(upload(S,key).andExpect(status().isCreated())).get("messageId").asText();
        assertThat(body(upload(S,key).andExpect(status().isOk())).get("messageId").asText()).isEqualTo(mid);
        var data=startBody(mid);String sid=carSend("/vehicles/"+vehicle+"/sessions",data,200).get("sessionId").asText();
        assertThat(carSend("/vehicles/"+vehicle+"/sessions",data,200).get("sessionId").asText()).isEqualTo(sid);
    }
    @Test void expiryPreventsDownloadAndNewPlaybackAndRemovesAudio() throws Exception {
        setup();String mid=message(),sid=start(mid);
        db.update("UPDATE cm_messages SET expires_at=? WHERE id=?",System.currentTimeMillis()-1,mid);
        mvc.perform(get("/api/v2"+path(sid,mid)+"/audio").header("Authorization",R).header("X-Vehicle-Token",deviceToken)).andExpect(status().isGone());
        mvc.perform(get("/api/v2"+path(sid,mid)).header("Authorization",R).header("X-Vehicle-Token",deviceToken)).andExpect(status().isGone());
        carSend(path(sid,mid)+"/events",event("PLAYING",0,uuid()),410);
        service.sweep();assertThat(db.queryForObject("SELECT audio_removed FROM cm_messages WHERE id=?",Boolean.class,mid)).isTrue();
        try(var files=Files.list(DIR)) { assertThat(files.count()).isZero(); }
    }
    @Test void destinationChangeEndsSessionAndBlocksOldAudio() throws Exception {
        setup();String mid=message(),sid=start(mid);
        mvc.perform(req("/sessions/"+sid+"/end",R,Map.of("reason","DESTINATION_CHANGED")).header("X-Vehicle-Token",deviceToken)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v2"+path(sid,mid)+"/audio").header("Authorization",R).header("X-Vehicle-Token",deviceToken)).andExpect(status().isConflict());
        carSend(path(sid,mid)+"/events",event("PLAYING",0,uuid()),409);
    }
    @Test void disconnectAndRePairRevokeOldDevice() throws Exception {
        setup();String old=deviceToken;deviceToken=pairCode();
        mvc.perform(get("/api/v2/vehicles/"+vehicle+"/access").header("Authorization",R).header("X-Vehicle-Token",old)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v2/vehicles/"+vehicle+"/connection").header("Authorization",S)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v2/vehicles/"+vehicle+"/access").header("Authorization",R).header("X-Vehicle-Token",deviceToken)).andExpect(status().isNotFound());
    }
    @Test void memberRemovalRevokesSessions() throws Exception {
        setup();String mid=message(),sid=start(mid);
        String oldInvite=send("/families/"+family+"/invitations",S,Map.of(),200).get("code").asText();
        mvc.perform(delete("/api/v2/families/"+family+"/members/demo-recipient").header("Authorization",S)).andExpect(status().isNoContent());
        send("/families/join",R,Map.of("code",oldInvite),409);
        mvc.perform(get("/api/v2"+path(sid,mid)+"/audio").header("Authorization",R).header("X-Vehicle-Token",deviceToken)).andExpect(status().isNotFound());
    }
    @Test void deleteMessageAndAccountBlockFutureUse() throws Exception {
        setup();String mid=message(),sid=start(mid);
        mvc.perform(delete("/api/v2/messages/"+mid).header("Authorization",S)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v2"+path(sid,mid)+"/audio").header("Authorization",R).header("X-Vehicle-Token",deviceToken)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v2/me").header("Authorization",R)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v2/me").header("Authorization",R)).andExpect(status().isForbidden());
    }
    @Test void conflictingEventsAndPrematureReplyAreRejected() throws Exception {
        setup();String mid=message(),sid=start(mid);
        carSend(path(sid,mid)+"/replies",Map.of("replyId",uuid(),"kind","THANK_YOU","parkedConfirmed",true),409);
        String eventId=uuid();carSend(path(sid,mid)+"/events",event("PLAYING",0,eventId),200);
        carSend(path(sid,mid)+"/events",event("HELD",0,eventId),409);
        carSend(path(sid,mid)+"/events",event("COMPLETED",0,uuid()),409);
    }
    @Test void forgottenTripCannotRemainActiveForever() throws Exception {
        setup();String mid=message(),sid=start(mid);
        db.update("UPDATE cm_sessions SET created_at=0 WHERE id=?",sid);
        mvc.perform(get("/api/v2"+path(sid,mid)).header("Authorization",R).header("X-Vehicle-Token",deviceToken)).andExpect(status().isConflict());
        service.sweep();assertThat(db.queryForObject("SELECT active FROM cm_sessions WHERE id=?",Boolean.class,sid)).isFalse();
    }
    @Test void retentionPurgesExpiredRecordsAfterThirtyDays() throws Exception {
        setup();String mid=message();db.update("UPDATE cm_messages SET expires_at=0 WHERE id=?",mid);
        assertThat(service.sweep()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM cm_messages",Integer.class)).isZero();
    }
}
