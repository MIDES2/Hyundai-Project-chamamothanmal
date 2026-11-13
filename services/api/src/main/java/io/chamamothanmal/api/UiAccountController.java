package io.chamamothanmal.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2")
public class UiAccountController {
    public record Register(@NotBlank @Size(max=30) String name,@Size(max=100) String handle,
        @Size(max=20) String phone,@NotBlank @Size(min=8,max=128) String password,@NotBlank String role) { }
    public record Login(@NotBlank @Size(max=100) String login,@NotBlank @Size(max=128) String password) { }
    private final UiAccountService accounts;
    public UiAccountController(UiAccountService accounts) { this.accounts=accounts; }
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    Object register(@Valid @RequestBody Register body,HttpServletRequest request) {
        accounts.requireLocal();accounts.throttle(request.getRemoteAddr());return accounts.register(body);
    }
    @PostMapping("/auth/login") Object login(@Valid @RequestBody Login body,HttpServletRequest request) {
        accounts.requireLocal();accounts.throttle(request.getRemoteAddr());return accounts.login(body.login(),body.password());
    }
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) { accounts.logout(request.getHeader("Authorization").substring(7)); }
    @GetMapping("/profile") Object profile(HttpServletRequest request) { return accounts.profile(PrototypeAuth.actor(request).id(),true); }
    @GetMapping("/friends/profiles") Object friends(HttpServletRequest request) { return accounts.friends(PrototypeAuth.actor(request).id()); }
    @GetMapping("/accounts/search") Object search(@RequestParam @Size(max=100) String query,HttpServletRequest request) {
        accounts.throttle(request.getRemoteAddr());return accounts.search(PrototypeAuth.actor(request).id(),query);
    }
}
