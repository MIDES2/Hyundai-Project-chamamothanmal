package io.chamamothanmal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Test-account authentication only; deliberately not a public sign-up/login system. */
@Component
@Order(10)
public class PrototypeAuth extends OncePerRequestFilter {
    public static final String ACTOR = PrototypeAuth.class.getName() + ".actor";
    public record Actor(String id, String role) { }
    private record Credential(byte[] token, Actor actor) { }
    private final List<Credential> credentials;
    private final ObjectMapper json;
    private final UiAccountService uiAccounts;
    private final CloudSettings cloudSettings;
    private final SupabaseGateway cloud;

    public PrototypeAuth(ApiProperties settings, ObjectMapper json, CloudSettings cloudSettings, SupabaseGateway cloud, UiAccountService uiAccounts) {
        this.uiAccounts=uiAccounts;
        this.cloudSettings = cloudSettings; this.cloud = cloud; this.json = json;
        if (cloudSettings.authMode.equals("supabase")) { credentials = List.of(); return; }
        if (!"development".equals(settings.environment())) {
            throw new IllegalArgumentException("Prototype authentication is allowed only with APP_ENV=development.");
        }
        List<String> tokens = List.of(settings.senderToken(), settings.recipientToken(), settings.otherRecipientToken());
        if (tokens.stream().anyMatch(t -> t == null || !t.matches("[A-Za-z0-9_-]{32,256}"))
            || new HashSet<>(tokens).size() != 3) {
            throw new IllegalArgumentException("Configure three distinct API_*_TOKEN values (32-256 URL-safe characters) in local .env.");
        }
        credentials = List.of(
            new Credential(tokens.get(0).getBytes(StandardCharsets.UTF_8), new Actor("demo-sender", "SENDER")),
            new Credential(tokens.get(1).getBytes(StandardCharsets.UTF_8), new Actor("demo-recipient", "RECIPIENT")),
            new Credential(tokens.get(2).getBytes(StandardCharsets.UTF_8), new Actor("demo-other", "RECIPIENT")));

    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/") || request.getMethod().equals("OPTIONS") ||
            (request.getMethod().equals("POST") && (path.equals("/api/v2/auth/login") || path.equals("/api/v2/auth/register")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (cloudSettings.authMode.equals("supabase")) {
            if (!request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/v2/")) {
                response.setStatus(404); return;
            }
            if (header != null && header.startsWith("Bearer ") && header.length() > 7 && header.length() < 16384) {
                String userId;
                try { userId = cloud.userId(header.substring(7)); }
                catch (ApiException ex) {
                    response.setStatus(ex.status().value()); response.setContentType("application/json");
                    json.writeValue(response.getOutputStream(), ApiErrors.body(ex.code(), ex.getMessage())); return;
                }
                request.setAttribute(ACTOR, new Actor(userId, "MEMBER")); chain.doFilter(request, response); return;
            }
        }
        if (header != null && header.startsWith("Bearer ") && header.length() <= 263) {
            byte[] supplied = header.substring(7).getBytes(StandardCharsets.UTF_8);
            for (Credential credential : credentials) {
                if (MessageDigest.isEqual(supplied, credential.token())) {
                    request.setAttribute(ACTOR, credential.actor());
                    chain.doFilter(request, response);
                    return;
                }
            }
        }
        if (header != null && header.startsWith("Bearer ") && header.substring(7).matches("[A-Za-z0-9_-]{43}")) {
            String user=uiAccounts.authenticate(header.substring(7));
            if(user!=null) {
                request.setAttribute(ACTOR,new Actor(user,"MEMBER"));chain.doFilter(request,response);return;
            }
        }
        response.setStatus(401);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        json.writeValue(response.getOutputStream(), ApiErrors.body("UNAUTHORIZED", "A valid bearer token is required."));
    }

    public static Actor actor(HttpServletRequest request) {
        return (Actor) request.getAttribute(ACTOR);
    }
}
