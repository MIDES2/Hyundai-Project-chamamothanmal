package io.chamamothanmal.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.cleanup-enabled",havingValue="true",matchIfMissing=true)
public class RetentionJob {
    private final FamilyService service;
    private final FamilyRepository db;
    private final VoiceBlobStore blobs;
    private final java.time.Clock clock;
    public RetentionJob(FamilyService service, FamilyRepository db, VoiceBlobStore blobs, java.time.Clock clock) {
        this.service = service;
        this.db = db;
        this.blobs = blobs;
        this.clock = clock;
    }
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void cleanup() {
        service.sweep();
        long now = clock.millis();
        for (var row : db.list("""
            SELECT m.id,m.audio_name FROM cm_friend_messages m
            WHERE m.audio_removed=FALSE AND (m.deleted_at IS NOT NULL
              OR (m.completed_at IS NULL AND m.expires_at<=?)
              OR (m.completed_at IS NOT NULL AND m.archive_until<=?)
              OR EXISTS (SELECT 1 FROM cm_users u WHERE u.state<>'ACTIVE'
                  AND (u.id=m.sender_id OR u.id=m.recipient_id)))
            LIMIT 100
            """, now, now)) {
            try {
                blobs.delete((String)row.get("audioName"));
                db.update("UPDATE cm_friend_messages SET audio_removed=TRUE WHERE id=?", row.get("id"));
            } catch (ApiException ignored) {
                // Retry storage failures on the next sweep without logging message data.
            }
        }
    }
}
