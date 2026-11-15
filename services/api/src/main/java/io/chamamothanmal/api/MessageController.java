package io.chamamothanmal.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static io.chamamothanmal.api.MessageDtos.*;

@RestController
public class MessageController {
    private final MessageService messages;
    public MessageController(MessageService messages) { this.messages = messages; }

    @GetMapping("/health")
    Map<String, String> health() { return Map.of("status", "UP"); }

    @PostMapping(value = "/api/v1/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<CreatedMessage> register(@Valid @ModelAttribute CreateMessage form,
        @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        var result = messages.register(PrototypeAuth.actor(request), key, form);
        return ResponseEntity.status(result.replayed() ? 200 : 201)
            .location(URI.create("/api/v1/messages/" + result.id()))
            .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
            .body(new CreatedMessage(result.id()));
    }

    @GetMapping("/api/v1/messages")
    MessageList list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledDate,
        @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "20") int limit, HttpServletRequest request) {
        return messages.list(PrototypeAuth.actor(request), scheduledDate, offset, limit);
    }

    @GetMapping("/api/v1/messages/{id}")
    MessageView detail(@PathVariable String id, HttpServletRequest request) {
        return messages.detail(PrototypeAuth.actor(request), id);
    }

    @GetMapping("/api/v1/messages/{id}/audio")
    ResponseEntity<Resource> audio(@PathVariable String id, HttpServletRequest request) {
        Resource audio = messages.audio(PrototypeAuth.actor(request), id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=voice.wav").body(audio);
    }

    @PostMapping("/api/v1/messages/{id}/playback-events")
    PlaybackReceipt playback(@PathVariable String id, @Valid @RequestBody PlaybackEvent event, HttpServletRequest request) {
        return messages.playback(PrototypeAuth.actor(request), id, event);
    }
}
