package io.chamamothanmal.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import static io.chamamothanmal.api.FamilyDtos.*;

@RestController
@RequestMapping("/api/v2")
public class FamilyController {
    private final FamilyService service;
    public FamilyController(FamilyService service) { this.service=service; }
    private String user(HttpServletRequest r) { return PrototypeAuth.actor(r).id(); }
    private String token(HttpServletRequest r) { return r.getHeader("X-Vehicle-Token"); }
    @GetMapping("/me") Object me(HttpServletRequest r) { return service.me(user(r)); }
    @DeleteMapping("/me") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAccount(HttpServletRequest r) { service.deleteAccount(user(r)); }
    @PostMapping("/families") @ResponseStatus(HttpStatus.CREATED)
    Object family(@Valid @RequestBody Name body,HttpServletRequest r) { return service.createFamily(user(r),body.name()); }
    @GetMapping("/families/{id}/members")
    Object members(@PathVariable String id,HttpServletRequest r) { return Map.of("items",service.members(user(r),id)); }
    @PostMapping("/families/{id}/invitations")
    Object invitation(@PathVariable String id,HttpServletRequest r) { return service.code(user(r),id,null); }
    @PostMapping("/families/join")
    Object join(@Valid @RequestBody Code body,HttpServletRequest r) { return service.join(user(r),body.code()); }
    @PatchMapping("/families/{id}/members/{memberId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void permission(@PathVariable String id,@PathVariable String memberId,@Valid @RequestBody Permission body,HttpServletRequest r) {
        service.permission(user(r),id,memberId,body.canSend());
    }
    @DeleteMapping("/families/{id}/members/{memberId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable String id,@PathVariable String memberId,HttpServletRequest r) { service.revokeMember(user(r),id,memberId); }
    @PostMapping("/vehicles") @ResponseStatus(HttpStatus.CREATED)
    Object vehicle(@Valid @RequestBody Vehicle body,HttpServletRequest r) { return service.createVehicle(user(r),body.familyId(),body.name()); }
    @GetMapping("/families/{id}/vehicles")
    Object vehicles(@PathVariable String id,HttpServletRequest r) { return Map.of("items",service.vehicles(user(r),id)); }
    @PostMapping("/vehicles/{id}/pairing-codes")
    Object pairingCode(@PathVariable String id,HttpServletRequest r) {
        String f=service.vehiclesFamily(user(r),id); return service.code(user(r),f,id);
    }
    @PostMapping("/vehicles/pair")
    Object pair(@Valid @RequestBody Code body,HttpServletRequest r) { return service.pair(user(r),body.code()); }
    @GetMapping("/vehicles/{id}/access")
    Object access(@PathVariable String id,HttpServletRequest r) { return service.access(user(r),id,token(r)); }
    @DeleteMapping("/vehicles/{id}/connection") @ResponseStatus(HttpStatus.NO_CONTENT)
    void disconnect(@PathVariable String id,HttpServletRequest r) { service.disconnect(user(r),id); }
    @PostMapping(value="/messages",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<?> register(@Valid @ModelAttribute Message body,@RequestHeader("Idempotency-Key") String key,HttpServletRequest r) {
        var result=service.register(user(r),key,body);
        return ResponseEntity.status(Boolean.TRUE.equals(result.get("replayed"))?200:201).body(result);
    }
    @GetMapping("/messages/sent")
    Object sent(HttpServletRequest r) { return Map.of("items",service.sent(user(r))); }
    @GetMapping("/messages/{id}")
    Object detail(@PathVariable String id,HttpServletRequest r) { return service.sentDetail(user(r),id); }
    @DeleteMapping("/messages/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id,HttpServletRequest r) { service.deleteMessage(user(r),id); }
    @GetMapping("/vehicles/{id}/messages")
    Object candidates(@PathVariable String id,HttpServletRequest r) { return Map.of("items",service.candidates(user(r),id,token(r))); }
    @PostMapping("/vehicles/{id}/sessions")
    Object start(@PathVariable String id,@Valid @RequestBody Start body,HttpServletRequest r) { return service.start(user(r),id,token(r),body); }
    @GetMapping("/sessions/{id}")
    Object session(@PathVariable String id,HttpServletRequest r) { return service.sessionDetail(user(r),id,token(r)); }
    @PostMapping("/sessions/{id}/end") @ResponseStatus(HttpStatus.NO_CONTENT)
    void end(@PathVariable String id,@Valid @RequestBody End body,HttpServletRequest r) { service.end(user(r),id,token(r),body.reason()); }
    @GetMapping("/sessions/{sessionId}/messages/{messageId}")
    Object delivery(@PathVariable String sessionId,@PathVariable String messageId,HttpServletRequest r) {
        return service.deliveryDetail(user(r),sessionId,messageId,token(r));
    }
    @GetMapping("/sessions/{sessionId}/messages/{messageId}/audio")
    ResponseEntity<byte[]> audio(@PathVariable String sessionId,@PathVariable String messageId,HttpServletRequest r) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav"))
            .header("Content-Disposition","inline; filename=voice.wav").body(service.audio(user(r),sessionId,messageId,token(r)));
    }
    @PostMapping("/sessions/{sessionId}/messages/{messageId}/events")
    Object event(@PathVariable String sessionId,@PathVariable String messageId,@Valid @RequestBody Event body,HttpServletRequest r) {
        return service.event(user(r),sessionId,messageId,token(r),body);
    }
    @PostMapping("/sessions/{sessionId}/messages/{messageId}/replies")
    Object reply(@PathVariable String sessionId,@PathVariable String messageId,@Valid @RequestBody Reply body,HttpServletRequest r) {
        return service.reply(user(r),sessionId,messageId,token(r),body);
    }
}
