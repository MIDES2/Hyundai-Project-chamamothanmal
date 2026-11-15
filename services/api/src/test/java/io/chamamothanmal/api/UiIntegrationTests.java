package io.chamamothanmal.api;

import com.fasterxml.jackson.databind.*;
import java.nio.*;
import java.time.*;
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
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class UiIntegrationTests {
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->"jdbc:h2:mem:ui-integration;DB_CLOSE_DELAY=-1");
        r.add("app.upload-dir",()->"./build/ui-integration-uploads");
    }
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate db;
    String suffix,remote;
    @BeforeEach void unique() { suffix=UUID.randomUUID().toString().substring(0,8);remote="test-"+suffix; }
    JsonNode body(ResultActions r) throws Exception { return json.readTree(r.andReturn().getResponse().getContentAsByteArray()); }
    Map<String,Object> form(String handle,String role,String phone) {
        return Map.of("name","Synthetic "+role,"handle",handle,"phone",phone,"password","Synthetic-pass-42!","role",role);
    }
    ResultActions postJson(String path,Object data) throws Exception {
        return mvc.perform(post("/api/v2"+path).with(r->{r.setRemoteAddr(remote);return r;}).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(data)));
    }
    String register(String handle,String role,String phone) throws Exception {
        return body(postJson("/auth/register",form(handle,role,phone)).andExpect(status().isCreated())).get("id").asText();
    }
    String login(String handle) throws Exception {
        return "Bearer "+body(postJson("/auth/login",Map.of("login",handle,"password","Synthetic-pass-42!")).andExpect(status().isOk())).get("accessToken").asText();
    }
    @Test void passwordsAreVerifiedSessionsAreRevokedAndAccountsAreNotInvented() throws Exception {
        String handle="sender-"+suffix;String id=register(handle,"recorder","");
        postJson("/auth/login",Map.of("login",handle,"password","wrong")).andExpect(status().isUnauthorized());
        postJson("/auth/register",form(handle.toUpperCase(Locale.ROOT),"driver","")).andExpect(status().isConflict());
        String token=login(handle);
        mvc.perform(get("/api/v2/profile").header("Authorization",token)).andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.password").doesNotExist());
        assertThat(db.queryForObject("SELECT password_hash FROM cm_ui_accounts WHERE id=?",String.class,id)).isNotEqualTo("Synthetic-pass-42!");
        mvc.perform(post("/api/v2/auth/logout").header("Authorization",token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v2/profile").header("Authorization",token)).andExpect(status().isUnauthorized());
    }
    @Test void exactHandleOrPhoneSearchCreatesReciprocalOppositeRoleFriends() throws Exception {
        String sender=register("sender-"+suffix,"recorder","");
        String phone="010"+String.format("%08d",Math.abs(suffix.hashCode()%100000000));
        String driver=register("driver-"+suffix,"driver",phone);
        register("same-"+suffix,"recorder","");
        String s=login("sender-"+suffix),d=login(phone);
        mvc.perform(get("/api/v2/accounts/search").param("query",phone).header("Authorization",s)).andExpect(jsonPath("$[0].id").value(driver)).andExpect(jsonPath("$[0].phone").value(""));
        mvc.perform(get("/api/v2/accounts/search").param("query",phone.substring(0,6)).header("Authorization",s)).andExpect(content().json("[]"));
        mvc.perform(put("/api/v2/friends").header("Authorization",s).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("accountId",driver)))).andExpect(status().isOk());
        mvc.perform(get("/api/v2/friends/profiles").header("Authorization",d)).andExpect(jsonPath("$[0].id").value(sender));
        mvc.perform(put("/api/v2/friends").header("Authorization",s).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("accountId",sender)))).andExpect(status().isBadRequest());
    }
    @Test void browserPreflightAllowsPutAndUntrustedOriginsAreRejected() throws Exception {
        mvc.perform(options("/api/v2/friends").header("Origin","http://localhost:5173").header("Access-Control-Request-Method","PUT")
            .header("Access-Control-Request-Headers","authorization,content-type")).andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
        mvc.perform(options("/api/v2/friends").header("Origin","https://untrusted.invalid").header("Access-Control-Request-Method","PUT")).andExpect(status().isForbidden());
    }
    byte[] wav() {
        var b=ByteBuffer.allocate(32044).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(32036).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)1);
        b.putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(32000);return b.array();
    }
    @Test void futureScheduledAudioIsHiddenUntilStartAndCompletionIsRecipientOnly() throws Exception {
        register("sender-"+suffix,"recorder","");String driver=register("driver-"+suffix,"driver","");
        String s=login("sender-"+suffix),d=login("driver-"+suffix);
        mvc.perform(put("/api/v2/friends").header("Authorization",s).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("accountId",driver)))).andExpect(status().isOk());
        LocalDate day=LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(2);
        var message=new MockMultipartFile("message","","application/json",json.writeValueAsBytes(Map.of("recipientId",driver,"placeName","Synthetic pin","latitude",37,"longitude",127,"listeningMode","PRIVATE","scheduledDate",day.toString())));
        var audio=new MockMultipartFile("audio","voice.wav","audio/wav",wav());String key=UUID.randomUUID().toString();
        JsonNode sent=body(mvc.perform(multipart("/api/v2/friend-messages").file(message).file(audio).header("Authorization",s).header("Idempotency-Key",key)).andExpect(status().isOk()));
        String id=sent.get("id").asText(),base="/api/v2/friend-messages/"+id;
        assertThat(sent.get("availableAt").asLong()).isEqualTo(day.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli());
        assertThat(sent.get("expiresAt").asLong()-sent.get("availableAt").asLong()).isEqualTo(86400000L);
        mvc.perform(get("/api/v2/friend-messages/inbox").header("Authorization",d)).andExpect(content().json("[]"));
        mvc.perform(get(base+"/audio").header("Authorization",d)).andExpect(status().isNotFound());
        mvc.perform(put(base+"/completion").header("Authorization",d)).andExpect(status().isNotFound());
        db.update("UPDATE cm_friend_messages SET available_at=? WHERE id=?",System.currentTimeMillis()-1,id);
        mvc.perform(get(base+"/audio").header("Authorization",s)).andExpect(status().isNotFound());
        mvc.perform(get(base+"/audio").header("Authorization",d)).andExpect(status().isOk()).andExpect(content().bytes(wav()));
        mvc.perform(put(base+"/reaction").header("Authorization",d).contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"👍\"}")).andExpect(status().isConflict());
        long completed=body(mvc.perform(put(base+"/completion").header("Authorization",d)).andExpect(status().isOk())).get("completedAt").asLong();
        mvc.perform(put(base+"/completion").header("Authorization",d)).andExpect(jsonPath("$.completedAt").value(completed));
        mvc.perform(put(base+"/reaction").header("Authorization",d).contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"👍\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v2/friend-messages/sent").header("Authorization",s)).andExpect(jsonPath("$[0].reaction").value("👍"));
        mvc.perform(delete(base).header("Authorization",s)).andExpect(status().isNotFound());
        mvc.perform(delete(base).header("Authorization",d)).andExpect(status().isNoContent());
        mvc.perform(get(base+"/audio").header("Authorization",d)).andExpect(status().isNotFound());
    }
}
