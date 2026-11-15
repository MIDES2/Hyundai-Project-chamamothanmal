package io.chamamothanmal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v2")
@Transactional
public class FriendMessageController {
    public enum ListeningMode { TOGETHER, PRIVATE }

    public record FriendRequest(@NotBlank @Size(max=64) String accountId) { }

    public record SendRequest(
        @NotBlank @Size(max=64) String recipientId,
        @NotBlank @Size(max=120) String placeName,
        @Size(max=500) String address,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull ListeningMode listeningMode,
        @Positive Long expiresAt, LocalDate scheduledDate
    ) { }

    public record MessageView(String id, String senderId, String recipientId, String placeName,
        String address, double latitude, double longitude, ListeningMode listeningMode,
        double audioSeconds, long createdAt, long expiresAt, Long completedAt,
        Long archiveUntil, Long deletedAt, LocalDate scheduledDate, long availableAt, String reaction) { }

    private final UiAccountService accounts;
    private final FamilyRepository db;
    private final FamilyService users;
    private final AudioStorage validator;
    private final VoiceBlobStore blobs;
    private final Clock clock;
    private final ObjectMapper json;

    public FriendMessageController(FamilyRepository db, FamilyService users,
        AudioStorage validator, VoiceBlobStore blobs, Clock clock, ObjectMapper json, UiAccountService accounts) {
        this.accounts=accounts;
        this.db = db;
        this.users = users;
        this.validator = validator;
        this.blobs = blobs;
        this.clock = clock;
        this.json = json;
    }

    private String actor(HttpServletRequest request) {
        String id = PrototypeAuth.actor(request).id();
        users.checkActiveUser(id);
        return id;
    }

    @PutMapping("/friends")
    Map<String, String> addFriend(HttpServletRequest request, @Valid @RequestBody FriendRequest form) {
        String owner = actor(request);
        db.one("SELECT id FROM cm_users WHERE id=? AND state='ACTIVE'", form.accountId());
        accounts.connect(owner,form.accountId());
        return Map.of("accountId", form.accountId());
    }

    @GetMapping("/friends")
    List<Map<String, String>> friends(HttpServletRequest request) {
        return db.list("""
            SELECT f.friend_id FROM cm_friends f JOIN cm_users u ON u.id=f.friend_id
            WHERE f.owner_id=? AND u.state='ACTIVE' ORDER BY f.friend_id LIMIT 100
            """, actor(request)).stream().map(row -> Map.of("accountId", (String)row.get("friendId"))).toList();
    }

    @PostMapping(value="/friend-messages", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    MessageView send(HttpServletRequest request, @RequestHeader("Idempotency-Key") UUID key,
        @Valid @RequestPart("message") SendRequest form, @RequestPart("audio") MultipartFile file)
        throws com.fasterxml.jackson.core.JsonProcessingException {
        String sender = actor(request);
        var audio = validator.validate(file);
        String hash = AudioStorage.digest(json.writeValueAsBytes(List.of(form, audio.sha256())));
        var existing = db.list("SELECT * FROM cm_friend_messages WHERE sender_id=? AND request_id=?",
            sender, key.toString());
        if (!existing.isEmpty()) {
            var row = existing.get(0);
            if (!hash.equals(row.get("requestHash"))) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Use a new request ID.");
            }
            return view(row);
        }
        db.one("""
            SELECT f.friend_id FROM cm_friends f JOIN cm_users u ON u.id=f.friend_id
            WHERE f.owner_id=? AND f.friend_id=? AND u.state='ACTIVE'
            """, sender, form.recipientId());
        long now = clock.millis();
        LocalDate today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        if(form.scheduledDate()!=null && form.scheduledDate().isBefore(today)) throw ApiException.invalid("오늘 이후의 수신 시작일을 선택해주세요.");
        long available=form.scheduledDate()==null?now:Math.max(now,form.scheduledDate().atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli());
        long expires = form.expiresAt() == null ? available + Duration.ofDays(1).toMillis() : form.expiresAt();
        if (!Double.isFinite(form.latitude()) || !Double.isFinite(form.longitude())) {
            throw ApiException.invalid("Use finite coordinates.");
        }
        if (expires <= available) throw ApiException.invalid("Choose a future expiry.");
        String id = UUID.randomUUID().toString();
        String blob = blobs.save(audio);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) blobs.delete(blob);
            }
        });
        db.update("""
            INSERT INTO cm_friend_messages(id,sender_id,recipient_id,request_id,request_hash,
              place_name,address,latitude,longitude,listening_mode,audio_name,audio_seconds,created_at,expires_at,scheduled_date,available_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, id, sender, form.recipientId(), key.toString(), hash, form.placeName(),
            form.address(), form.latitude(), form.longitude(), form.listeningMode().name(),
            blob, audio.seconds(), now, expires, form.scheduledDate(), available);
        return view(db.one("SELECT * FROM cm_friend_messages WHERE id=?", id));
    }

    @GetMapping("/friend-messages/inbox")
    List<MessageView> inbox(HttpServletRequest request) {
        return db.list("""
            SELECT m.* FROM cm_friend_messages m JOIN cm_users u ON u.id=m.sender_id
            WHERE m.recipient_id=? AND m.deleted_at IS NULL AND m.completed_at IS NULL
              AND m.available_at<=? AND m.expires_at>? AND u.state='ACTIVE' ORDER BY m.created_at DESC LIMIT 100
            """, actor(request), clock.millis(), clock.millis()).stream().map(this::view).toList();
    }

    @GetMapping("/friend-messages/archive")
    List<MessageView> archive(HttpServletRequest request) {
        return db.list("""
            SELECT m.* FROM cm_friend_messages m JOIN cm_users u ON u.id=m.sender_id
            WHERE m.recipient_id=? AND m.deleted_at IS NULL AND m.completed_at IS NOT NULL
              AND m.archive_until>? AND m.audio_removed=FALSE AND u.state='ACTIVE'
            ORDER BY m.completed_at DESC LIMIT 100
            """, actor(request), clock.millis()).stream().map(this::view).toList();
    }

    @GetMapping("/friend-messages/sent")
    List<MessageView> sent(HttpServletRequest request) {
        return db.list("""
            SELECT * FROM cm_friend_messages WHERE sender_id=? AND deleted_at IS NULL
            ORDER BY created_at DESC LIMIT 100
            """, actor(request)).stream().map(this::view).toList();
    }

    @GetMapping("/friend-messages/{id}/audio")
    ResponseEntity<byte[]> audio(HttpServletRequest request, @PathVariable UUID id) {
        var row = received(actor(request), id);
        checkAudioAccess(row);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType("audio/wav")).body(blobs.read((String)row.get("audioName")));
    }

    @PutMapping("/friend-messages/{id}/completion")
    MessageView complete(HttpServletRequest request, @PathVariable UUID id) {
        var row = received(actor(request), id);
        if (row.get("completedAt") != null) return view(row);
        checkAudioAccess(row);
        long now = clock.millis();
        db.update("UPDATE cm_friend_messages SET completed_at=?, archive_until=? WHERE id=?",
            now, now + Duration.ofDays(7).toMillis(), id.toString());
        return view(received((String)row.get("recipientId"), id));
    }

    @DeleteMapping("/friend-messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(HttpServletRequest request, @PathVariable UUID id) {
        String user = actor(request);
        var row = db.one("SELECT * FROM cm_friend_messages WHERE id=? AND recipient_id=? FOR UPDATE",
            id.toString(), user);
        if (row.get("deletedAt") == null) {
            db.update("UPDATE cm_friend_messages SET deleted_at=? WHERE id=?", clock.millis(), id.toString());
        }
    }

    public record Reaction(@NotBlank @Size(max=16) String emoji) { }
    @PutMapping("/friend-messages/{id}/reaction")
    MessageView reaction(HttpServletRequest request,@PathVariable UUID id,@Valid @RequestBody Reaction body) {
        var row=received(actor(request),id);checkAudioAccess(row);
        if(row.get("completedAt")==null) throw new ApiException(HttpStatus.CONFLICT,"NOT_COMPLETED","끝까지 재생한 후 답장할 수 있습니다.");
        if(!java.util.Set.of("👍","🤣","🤔","🫠","🙂‍↕️","🥹","😭","♥️","😡","🥺").contains(body.emoji()))
            throw ApiException.invalid("지원하는 이모지를 선택해주세요.");
        db.update("UPDATE cm_friend_messages SET reaction=? WHERE id=?",body.emoji(),id.toString());
        return view(received((String)row.get("recipientId"),id));
    }

    private Map<String,Object> received(String user, UUID id) {
        return db.one("""
            SELECT m.* FROM cm_friend_messages m WHERE m.id=? AND m.recipient_id=?
            AND m.deleted_at IS NULL AND EXISTS
                (SELECT 1 FROM cm_users u WHERE u.id=m.sender_id AND u.state='ACTIVE') FOR UPDATE
            """, id.toString(), user);
    }

    private void checkAudioAccess(Map<String,Object> row) {
        Long until = longValue(row, row.get("completedAt") == null ? "expiresAt" : "archiveUntil");
        if (((Number)row.get("availableAt")).longValue()>clock.millis() || Boolean.TRUE.equals(row.get("audioRemoved")) || until == null || until <= clock.millis()) {
            throw ApiException.notFound();
        }
    }

    private static Long longValue(Map<String,Object> row, String field) {
        return row.get(field) instanceof Number number ? number.longValue() : null;
    }

    private MessageView view(Map<String,Object> row) {
        return new MessageView((String)row.get("id"), (String)row.get("senderId"), (String)row.get("recipientId"),
            (String)row.get("placeName"), (String)row.get("address"), ((Number)row.get("latitude")).doubleValue(),
            ((Number)row.get("longitude")).doubleValue(), ListeningMode.valueOf((String)row.get("listeningMode")),
            ((Number)row.get("audioSeconds")).doubleValue(), longValue(row,"createdAt"), longValue(row,"expiresAt"),
            longValue(row,"completedAt"), longValue(row,"archiveUntil"), longValue(row,"deletedAt"),
            row.get("scheduledDate")==null?null:LocalDate.parse(row.get("scheduledDate").toString()), ((Number)row.get("availableAt")).longValue(), (String)row.get("reaction"));
    }
}
