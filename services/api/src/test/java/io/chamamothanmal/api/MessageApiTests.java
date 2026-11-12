package io.chamamothanmal.api;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageApiTests {
    private static final String ROOT = "/api/v1/messages";
    private static final String SENDER = "Bearer test_sender_token_0000000000000000000001";
    private static final String RECIPIENT = "Bearer test_recipient_token_000000000000000001";
    private static final String OTHER = "Bearer test_other_token_000000000000000000001";
    private static final Path UPLOADS;
    static {
        try { UPLOADS = Files.createDirectories(Path.of("build", "test-uploads-" + UUID.randomUUID()).toAbsolutePath()); }
        catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry properties) {
        properties.add("app.upload-dir", UPLOADS::toString);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void clean() throws Exception {
        jdbc.update("DELETE FROM playback_events");
        jdbc.update("DELETE FROM messages");
        try (var files = Files.list(UPLOADS)) {
            for (Path p : files.toList()) Files.delete(p);
        }
    }

    private String today() { return LocalDate.now(ZoneId.of("Asia/Seoul")).toString(); }
    private byte[] wav(int seconds) {
        int count = seconds * 16000 * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + count).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(36 + count);
        buffer.put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16);
        buffer.putShort((short) 1).putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16);
        buffer.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(count);
        return buffer.array();
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder upload(byte[] bytes, String key) {
        return multipart(ROOT).file(new MockMultipartFile("audio", "../../unsafe.wav", "audio/wav", bytes))
            .param("recipientId", "demo-recipient").param("placeName", "Synthetic test place")
            .param("latitude", "37.0").param("longitude", "127.0").param("scheduledDate", today())
            .header("Authorization", SENDER).header("Idempotency-Key", key);
    }
    private String register() throws Exception {
        String result = mvc.perform(upload(wav(1), UUID.randomUUID().toString())).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("messageId").asText();
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder event(String id, String eventId, String state, long version) throws Exception {
        return post(ROOT + "/" + id + "/playback-events").header("Authorization", RECIPIENT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(Map.of("eventId", eventId, "status", state, "expectedVersion", version, "source", "MANUAL")));
    }

    @Test void registerListAndDownloadExactBytes() throws Exception {
        String id = register();
        mvc.perform(get(ROOT).header("Authorization", RECIPIENT)).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].messageId").value(id))
            .andExpect(jsonPath("$.items[0].playbackConditions.requireParked").value(true))
            .andExpect(jsonPath("$.items[0].playback.status").value("PENDING"));
        byte[] received = mvc.perform(get(ROOT + "/" + id + "/audio").header("Authorization", RECIPIENT))
            .andExpect(status().isOk()).andExpect(content().contentType("audio/wav"))
            .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsByteArray();
        assertThat(received).isEqualTo(wav(1));
        try (var files = Files.list(UPLOADS)) {
            assertThat(files.map(p -> p.getFileName().toString()).toList()).hasSize(1)
                .allMatch(name -> name.matches("[0-9a-f-]{36}\\.wav"));
        }
    }

    @Test void unauthorizedRequestsDoNotCreateOrRevealMessages() throws Exception {
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get(ROOT).header("Authorization", "Bearer wrong")).andExpect(status().isUnauthorized());
        mvc.perform(multipart(ROOT).file(new MockMultipartFile("audio", wav(1))))
            .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages", Long.class)).isZero();
    }

    @Test void otherRecipientCannotReadDownloadOrUpdate() throws Exception {
        String id = register();
        mvc.perform(get(ROOT).header("Authorization", OTHER)).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get(ROOT + "/" + id).header("Authorization", OTHER)).andExpect(status().isNotFound());
        mvc.perform(get(ROOT + "/" + id + "/audio").header("Authorization", OTHER)).andExpect(status().isNotFound());
        var request = event(id, UUID.randomUUID().toString(), "PLAYING", 0);
        request.header("Authorization", OTHER);
        // Replace rather than append Authorization: MockMvc headers can contain multiple values.
        request.with(r -> { r.removeHeader("Authorization"); r.addHeader("Authorization", OTHER); return r; });
        mvc.perform(request).andExpect(status().isNotFound());
    }

    @Test void recipientCannotRegisterAndSenderCannotDownload() throws Exception {
        var request = upload(wav(1), UUID.randomUUID().toString());
        request.with(r -> { r.removeHeader("Authorization"); r.addHeader("Authorization", RECIPIENT); return r; });
        mvc.perform(request).andExpect(status().isForbidden());
        String id = register();
        mvc.perform(get(ROOT + "/" + id + "/audio").header("Authorization", SENDER)).andExpect(status().isNotFound());
    }

    @Test void sameUploadKeyReplaysButDifferentContentConflicts() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = mvc.perform(upload(wav(1), key)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(upload(wav(1), key)).andExpect(status().isOk())
            .andExpect(header().string("Idempotency-Replayed", "true")).andExpect(content().json(first));
        mvc.perform(upload(wav(2), key)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages", Long.class)).isEqualTo(1);
        try (var files = Files.list(UPLOADS)) { assertThat(files.count()).isEqualTo(1); }
    }

    @Test void rejectsInvalidCoordinatesDatesAndIdentifiers() throws Exception {
        var invalidLatitude = upload(wav(1), UUID.randomUUID().toString());
        invalidLatitude.with(r -> { r.setParameter("latitude", "91"); return r; });
        mvc.perform(invalidLatitude).andExpect(status().isBadRequest());
        mvc.perform(upload(wav(1), "not-a-uuid")).andExpect(status().isBadRequest());
        var oldDate = upload(wav(1), UUID.randomUUID().toString());
        oldDate.with(r -> { r.setParameter("scheduledDate", "2000-01-01"); return r; });
        mvc.perform(oldDate).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages", Long.class)).isZero();
    }

    @Test void rejectsUnknownRecipient() throws Exception {
        var request = upload(wav(1), UUID.randomUUID().toString());
        request.with(r -> { r.setParameter("recipientId", "unapproved"); return r; });
        mvc.perform(request).andExpect(status().isForbidden());
    }

    @Test void rejectsFakeTruncatedEmptyAndLongAudio() throws Exception {
        mvc.perform(upload("not a real wave file".getBytes(), UUID.randomUUID().toString())).andExpect(status().isUnsupportedMediaType());
        mvc.perform(upload(new byte[0], UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        byte[] truncated = java.util.Arrays.copyOf(wav(1), 100);
        mvc.perform(upload(truncated, UUID.randomUUID().toString())).andExpect(status().isUnsupportedMediaType());
        mvc.perform(upload(wav(16), UUID.randomUUID().toString())).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages", Long.class)).isZero();
        try (var files = Files.list(UPLOADS)) { assertThat(files.count()).isZero(); }
    }

    @Test void rejectsOversizedAndMissingAudio() throws Exception {
        mvc.perform(upload(new byte[5_242_881], UUID.randomUUID().toString())).andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart(ROOT).header("Authorization", SENDER).header("Idempotency-Key", UUID.randomUUID().toString()))
            .andExpect(status().isBadRequest());
    }

    @Test void completedEventIsIdempotentAndTerminal() throws Exception {
        String id = register();
        String start = UUID.randomUUID().toString();
        mvc.perform(event(id, start, "PLAYING", 0)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        String end = UUID.randomUUID().toString();
        mvc.perform(event(id, end, "COMPLETED", 1)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(event(id, end, "COMPLETED", 1)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(event(id, UUID.randomUUID().toString(), "PLAYING", 2)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM playback_events", Long.class)).isEqualTo(2);
    }

    @Test void rejectsStaleEventsChangedPayloadAndCompletionBeforeStart() throws Exception {
        String id = register();
        mvc.perform(event(id, UUID.randomUUID().toString(), "COMPLETED", 0)).andExpect(status().isConflict());
        String eventId = UUID.randomUUID().toString();
        mvc.perform(event(id, eventId, "PLAYING", 0)).andExpect(status().isOk());
        mvc.perform(event(id, eventId, "PAUSED", 0)).andExpect(status().isConflict());
        mvc.perform(event(id, UUID.randomUUID().toString(), "PAUSED", 0)).andExpect(status().isConflict());
        mvc.perform(event(id, UUID.randomUUID().toString(), "PAUSED", 1)).andExpect(status().isOk());
    }

    @Test void holdsAndResumesWithoutRecordingVehicleTelemetry() throws Exception {
        String id = register();
        mvc.perform(event(id, UUID.randomUUID().toString(), "HELD", 0)).andExpect(status().isOk());
        mvc.perform(event(id, UUID.randomUUID().toString(), "PLAYING", 1)).andExpect(status().isOk());
        mvc.perform(event(id, UUID.randomUUID().toString(), "PAUSED", 2)).andExpect(status().isOk());
        mvc.perform(event(id, UUID.randomUUID().toString(), "PLAYING", 3)).andExpect(status().isOk());
    }

    @Test void filtersByDateAndValidatesPaging() throws Exception {
        register();
        mvc.perform(get(ROOT).param("scheduledDate", "2000-01-01").header("Authorization", RECIPIENT))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get(ROOT).param("limit", "101").header("Authorization", RECIPIENT)).andExpect(status().isBadRequest());
        mvc.perform(get(ROOT).param("offset", "-1").header("Authorization", RECIPIENT)).andExpect(status().isBadRequest());
    }

    @Test void futureAudioCanPrefetchButNotStartPlayback() throws Exception {
        String id = register();
        jdbc.update("UPDATE messages SET scheduled_date=? WHERE id=?", LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1), id);
        mvc.perform(get(ROOT + "/" + id + "/audio").header("Authorization", RECIPIENT)).andExpect(status().isOk());
        mvc.perform(event(id, UUID.randomUUID().toString(), "PLAYING", 0)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("NOT_SCHEDULED_TODAY"));
    }

    @Test void corsPreflightAllowsOnlyConfiguredOrigins() throws Exception {
        mvc.perform(options(ROOT).header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "authorization,idempotency-key"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options(ROOT).header("Origin", "https://not-approved.invalid").header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden());
    }

    @Test void missingStoredFileReturnsUnavailableWithoutLeakingPath() throws Exception {
        String id = register();
        try (var files = Files.list(UPLOADS)) { for (Path path : files.toList()) Files.delete(path); }
        mvc.perform(get(ROOT + "/" + id + "/audio").header("Authorization", RECIPIENT))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("AUDIO_UNAVAILABLE"));
    }
}
