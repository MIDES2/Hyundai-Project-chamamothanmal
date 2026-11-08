package io.chamamothanmal.api;

import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public final class FamilyDtos {
    private FamilyDtos() { }
    public record Name(@NotBlank @Size(max=120) String name) { }
    public record Code(@NotBlank @Size(max=128) String code) { }
    public record Vehicle(@NotBlank String familyId, @NotBlank @Size(max=120) String name) { }
    public record Permission(@NotNull Boolean canSend) { }
    public record Message(@NotBlank String vehicleId, @NotBlank @Size(max=120) String placeName,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Min(10) @Max(1000) Integer radiusMeters, @Min(1) @Max(24) Integer expiresInHours,
        @NotNull MultipartFile audio) { }
    public enum Source { MANUAL, SDK, MOCK }
    public record Start(@NotBlank String requestId, @NotNull Boolean consent,
        @NotNull Boolean parkedConfirmed,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double destinationLatitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double destinationLongitude,
        @NotNull Source source, @NotNull @Size(min=1,max=1) List<@NotBlank String> messageIds) { }
    public enum EndReason { ARRIVED, DESTINATION_CHANGED, CANCELLED, PROFILE_CHANGED, DISCONNECTED }
    public record End(@NotNull EndReason reason) { }
    public enum Status { PLAYING, PAUSED, HELD, COMPLETED, SKIPPED, FAILED }
    public enum Reason { USER_ACTION, STATE_UNKNOWN, DRIVING, DOOR_OPEN, AUDIO_FOCUS_LOST, PLAYBACK_ERROR, NETWORK_ERROR }
    public record Event(@NotBlank String eventId, @NotNull Status status, @NotNull @Min(0) Long expectedVersion,
        @NotNull Source source, Reason reason) { }
    public enum ReplyKind { THANK_YOU, ARRIVED_SAFELY }
    public record Reply(@NotBlank String replyId, @NotNull ReplyKind kind, @NotNull Boolean parkedConfirmed) { }
}
