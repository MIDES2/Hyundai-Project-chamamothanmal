package io.chamamothanmal.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

public final class MessageDtos {
    private MessageDtos() { }

    public record CreateMessage(
        @NotBlank @Size(max = 64) String recipientId,
        @NotBlank @Size(max = 120) String placeName,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledDate,
        @Min(10) @Max(1000) Integer radiusMeters,
        @NotNull MultipartFile audio) { }

    public record PlaybackConditions(int radiusMeters, boolean requireParked) { }
    public record PlaybackView(String status, long version) { }
    public record MessageView(String messageId, String placeName, double latitude, double longitude,
                              LocalDate scheduledDate, PlaybackConditions playbackConditions,
                              double audioDurationSeconds, String audioContentType, long audioSizeBytes,
                              String audioPath, PlaybackView playback) { }
    public record MessageList(List<MessageView> items, int offset, int limit, long total) { }
    public record CreatedMessage(String messageId) { }
    public enum PlaybackStatus { PENDING, PLAYING, PAUSED, HELD, COMPLETED, SKIPPED, FAILED }
    public enum PlaybackReason { USER_ACTION, STATE_UNKNOWN, DRIVING, DOOR_OPEN, AUDIO_FOCUS_LOST, NETWORK_ERROR, PLAYBACK_ERROR }
    public enum EventSource { MANUAL, SDK, MOCK }
    public record PlaybackEvent(
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String eventId,
        @NotNull PlaybackStatus status,
        @NotNull @Min(0) Long expectedVersion,
        @NotNull EventSource source,
        PlaybackReason reason) { }
    public record PlaybackReceipt(String messageId, String eventId, String status, long version) { }
}
