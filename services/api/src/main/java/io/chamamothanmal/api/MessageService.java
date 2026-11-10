package io.chamamothanmal.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static io.chamamothanmal.api.MessageDtos.*;
import static io.chamamothanmal.api.MessageRepository.StoredMessage;

@Service
public class MessageService {
    public record Registration(String id, boolean replayed) { }
    private final MessageRepository repository;
    private final AudioStorage storage;
    private final ObjectMapper json;
    private final ApiProperties settings;
    public MessageService(MessageRepository repository, AudioStorage storage, ObjectMapper json, ApiProperties settings) {
        this.repository = repository; this.storage = storage; this.json = json; this.settings = settings;
    }

    @Transactional
    public Registration register(PrototypeAuth.Actor actor, String requestId, CreateMessage form) {
        if (!"SENDER".equals(actor.role())) throw new ApiException(HttpStatus.FORBIDDEN, "SENDER_REQUIRED", "Only an approved sender may register messages.");
        requireUuid(requestId);
        if (!Double.isFinite(form.latitude()) || !Double.isFinite(form.longitude())) throw ApiException.invalid("Coordinates must be finite.");
        if (form.scheduledDate().isBefore(LocalDate.now(settings.timezone()))) throw ApiException.invalid("scheduledDate must be today or later in the configured timezone.");
        if (!repository.maySend(actor.id(), form.recipientId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "RECIPIENT_NOT_ALLOWED", "The sender is not authorized for this recipient.");
        }
        AudioStorage.ValidatedAudio audio = storage.validate(form.audio());
        int radius = form.radiusMeters() == null ? 100 : form.radiusMeters();
        String fingerprint;
        try {
            fingerprint = AudioStorage.digest(json.writeValueAsBytes(List.of(form.recipientId(), form.placeName().trim(),
                form.latitude(), form.longitude(), form.scheduledDate().toString(), radius, audio.sha256())));
        } catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
        repository.lockSender(actor.id());
        var previous = repository.byRequest(actor.id(), requestId);
        if (previous.isPresent()) {
            if (!previous.get().requestHash().equals(fingerprint)) throw conflict("IDEMPOTENCY_CONFLICT", "The same key was used for different content.");
            return new Registration(previous.get().id(), true);
        }
        String id = UUID.randomUUID().toString();
        String audioName = storage.save(audio);
        // Filesystem writes are not database transactions; undo the new file on DB rollback.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.delete(audioName);
            }
        });
        repository.insert(id, actor.id(), requestId, fingerprint, form, audioName, audio, radius);
        return new Registration(id, false);
    }

    public MessageList list(PrototypeAuth.Actor actor, LocalDate date, int offset, int limit) {
        requireRecipient(actor);
        if (offset < 0 || limit < 1 || limit > 100) throw ApiException.invalid("Use offset >= 0 and limit between 1 and 100.");
        return new MessageList(repository.list(actor.id(), date, offset, limit).stream().map(this::view).toList(),
            offset, limit, repository.count(actor.id(), date));
    }

    public MessageView detail(PrototypeAuth.Actor actor, String id) { return view(access(actor, id, false)); }
    public Resource audio(PrototypeAuth.Actor actor, String id) { return storage.resource(access(actor, id, false).audioName()); }

    private StoredMessage access(PrototypeAuth.Actor actor, String id, boolean lock) {
        requireRecipient(actor);
        return repository.get(id, actor.id(), lock).orElseThrow(ApiException::notFound);
    }

    private void requireRecipient(PrototypeAuth.Actor actor) {
        if (!"RECIPIENT".equals(actor.role())) throw ApiException.notFound();
    }

    private MessageView view(StoredMessage m) {
        return new MessageView(m.id(), m.placeName(), m.latitude(), m.longitude(), m.scheduledDate(),
            new PlaybackConditions(m.radiusMeters(), true), m.audioSeconds(), "audio/wav", m.audioBytes(),
            "/api/v1/messages/" + m.id() + "/audio", new PlaybackView(m.status(), m.version()));
    }

    @Transactional
    public PlaybackReceipt playback(PrototypeAuth.Actor actor, String id, PlaybackEvent event) {
        StoredMessage message = access(actor, id, true);
        requireUuid(event.eventId());
        List<Map<String, Object>> existing = repository.jdbc().queryForList(
            "SELECT * FROM playback_events WHERE message_id=? AND event_id=?", id, event.eventId());
        if (!existing.isEmpty()) {
            Map<String, Object> old = existing.get(0);
            if (!old.get("PLAYBACK_STATUS").equals(event.status().name())
                || ((Number) old.get("EXPECTED_VERSION")).longValue() != event.expectedVersion()
                || !old.get("EVENT_SOURCE").equals(event.source().name())
                || !Objects.equals(old.get("REASON"), event.reason() == null ? null : event.reason().name())) {
                throw conflict("EVENT_ID_CONFLICT", "The event ID was used for different content.");
            }
            return new PlaybackReceipt(id, event.eventId(), event.status().name(), ((Number) old.get("RESULT_VERSION")).longValue());
        }
        if (message.version() != event.expectedVersion()) throw conflict("VERSION_CONFLICT", "Refresh the message state before sending a new event.");
        PlaybackStatus before = PlaybackStatus.valueOf(message.status());
        if (!allowed(before, event.status())) throw conflict("INVALID_TRANSITION", "This playback status transition is not allowed.");
        if (event.status() == PlaybackStatus.PLAYING
            && !message.scheduledDate().equals(LocalDate.now(settings.timezone()))) {
            throw conflict("NOT_SCHEDULED_TODAY", "Playback may start only on scheduledDate in the configured timezone.");
        }
        long version = message.version() + 1;
        repository.jdbc().update("UPDATE messages SET playback_status=?,playback_version=? WHERE id=?", event.status().name(), version, id);
        repository.jdbc().update("""
            INSERT INTO playback_events (message_id,event_id,playback_status,expected_version,result_version,event_source,reason,recorded_at)
            VALUES (?,?,?,?,?,?,?,?)
            """, id, event.eventId(), event.status().name(), event.expectedVersion(), version,
            event.source().name(), event.reason() == null ? null : event.reason().name(), System.currentTimeMillis());
        return new PlaybackReceipt(id, event.eventId(), event.status().name(), version);
    }

    private boolean allowed(PlaybackStatus before, PlaybackStatus after) {
        if (before == PlaybackStatus.COMPLETED || before == PlaybackStatus.SKIPPED || after == PlaybackStatus.PENDING) return false;
        return switch (after) {
            case PLAYING -> Set.of(PlaybackStatus.PENDING, PlaybackStatus.PAUSED, PlaybackStatus.HELD, PlaybackStatus.FAILED).contains(before);
            case PAUSED, COMPLETED -> before == PlaybackStatus.PLAYING;
            case HELD, FAILED, SKIPPED -> before != after;
            default -> false;
        };
    }

    private void requireUuid(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw ApiException.invalid("Use a UUID for request/event identifiers.");
        }
    }

    private ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
}
