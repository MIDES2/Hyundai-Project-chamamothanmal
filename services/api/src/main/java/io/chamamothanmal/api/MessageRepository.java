package io.chamamothanmal.api;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MessageRepository {
    public record StoredMessage(String id, String senderId, String recipientId, String requestHash,
                                String placeName, double latitude, double longitude, LocalDate scheduledDate,
                                int radiusMeters, String audioName, long audioBytes, double audioSeconds,
                                String status, long version) { }
    private final JdbcTemplate jdbc;
    public MessageRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private StoredMessage map(ResultSet r, int row) throws SQLException {
        return new StoredMessage(r.getString("id"), r.getString("sender_id"), r.getString("recipient_id"),
            r.getString("request_hash"), r.getString("place_name"), r.getDouble("latitude"), r.getDouble("longitude"),
            r.getObject("scheduled_date", LocalDate.class), r.getInt("radius_meters"), r.getString("audio_name"),
            r.getLong("audio_bytes"), r.getDouble("audio_seconds"), r.getString("playback_status"), r.getLong("playback_version"));
    }

    public boolean maySend(String sender, String recipient) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM send_permissions WHERE sender_id=? AND recipient_id=?", Long.class, sender, recipient) > 0;
    }
    public void lockSender(String id) {
        jdbc.queryForObject("SELECT id FROM app_users WHERE id=? FOR UPDATE", String.class, id);
    }
    public Optional<StoredMessage> byRequest(String sender, String requestId) {
        return jdbc.query("SELECT * FROM messages WHERE sender_id=? AND request_id=?", this::map, sender, requestId).stream().findFirst();
    }
    public Optional<StoredMessage> get(String id, String recipient, boolean lock) {
        return jdbc.query("SELECT * FROM messages WHERE id=? AND recipient_id=?" + (lock ? " FOR UPDATE" : ""), this::map, id, recipient).stream().findFirst();
    }
    public List<StoredMessage> list(String recipient, LocalDate date, int offset, int limit) {
        if (date != null) return jdbc.query("SELECT * FROM messages WHERE recipient_id=? AND scheduled_date=? ORDER BY created_at DESC,id LIMIT ? OFFSET ?", this::map, recipient, date, limit, offset);
        return jdbc.query("SELECT * FROM messages WHERE recipient_id=? ORDER BY created_at DESC,id LIMIT ? OFFSET ?", this::map, recipient, limit, offset);
    }
    public long count(String recipient, LocalDate date) {
        return date == null ? jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE recipient_id=?", Long.class, recipient)
            : jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE recipient_id=? AND scheduled_date=?", Long.class, recipient, date);
    }
    public void insert(String id, String sender, String requestId, String hash, MessageDtos.CreateMessage form,
                       String audioName, AudioStorage.ValidatedAudio audio, int radius) {
        jdbc.update("""
            INSERT INTO messages (id,sender_id,recipient_id,request_id,request_hash,place_name,latitude,longitude,
              scheduled_date,radius_meters,require_parked,audio_name,audio_type,audio_bytes,audio_seconds,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,TRUE,?,'audio/wav',?,?,?)
            """, id, sender, form.recipientId(), requestId, hash, form.placeName().trim(), form.latitude(), form.longitude(),
            form.scheduledDate(), radius, audioName, audio.bytes().length, audio.seconds(), System.currentTimeMillis());
    }
    public JdbcTemplate jdbc() { return jdbc; }
}
