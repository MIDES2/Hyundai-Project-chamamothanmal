package io.chamamothanmal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Auth is verified online by Supabase; unverified JWT payloads are never trusted. */
@Component
public class SupabaseGateway {
    private final CloudSettings config;
    private final ObjectMapper json;
    private final HttpClient client;
    private final String origin;
    @org.springframework.beans.factory.annotation.Autowired
    public SupabaseGateway(CloudSettings config, ObjectMapper json) { this(config, json, config.url); }
    // Package-private seam for local HTTP contract tests; production origin is validated HTTPS.
    SupabaseGateway(CloudSettings config, ObjectMapper json, String origin) {
        this.config = config; this.json = json; this.origin = origin;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    private byte[] call(String method, String path, String key, String bearer, byte[] body, String type) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(origin + path))
                .timeout(Duration.ofSeconds(15)).header("apikey", key).header("Authorization", "Bearer " + bearer);
            if (type != null) request.header("Content-Type", type);
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
            HttpResponse<java.io.InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                if (method.equals("DELETE") && path.startsWith("/auth/v1/admin/users/") && response.statusCode()==404) return new byte[0];
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    if (path.equals("/auth/v1/user") && (response.statusCode() == 401 || response.statusCode() == 403))
                        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sign in again.");
                    throw unavailable();
                }
                byte[] bytes = stream.readNBytes(5_242_881);
                if (bytes.length > 5_242_880) throw unavailable();
                return bytes;
            }
        } catch (ApiException ex) { throw ex; }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (Exception ex) { throw unavailable(); }
    }
    public String userId(String accessToken) {
        byte[] bytes = call("GET", "/auth/v1/user", config.publishableKey, accessToken, null, null);
        try {
            String id = json.readTree(bytes).path("id").asText();
            return UUID.fromString(id).toString();
        } catch (Exception ex) { throw unavailable(); }
    }
    public void deleteAuthUser(String id) {
        String safeId = UUID.fromString(id).toString();
        if(config.serviceKey.isBlank()) throw unavailable();
        call("DELETE", "/auth/v1/admin/users/" + safeId, config.serviceKey, config.serviceKey, "{\"should_soft_delete\":false}".getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json");
    }
    private String objectPath(String name) {
        if (!name.matches("[0-9a-f-]{36}\\.wav")) throw new IllegalArgumentException("Invalid object name.");
        return "/storage/v1/object/" + config.bucket + "/" + name;
    }
    private void requirePrivateBucket() {
        byte[] response=call("GET", "/storage/v1/bucket/"+config.bucket,config.serviceKey,config.serviceKey,null,null);
        try {
            var value=json.readTree(response).get("public");
            if(value==null || !value.isBoolean() || value.asBoolean()) throw unavailable();
        } catch(ApiException ex) { throw ex; }
        catch(Exception ex) { throw unavailable(); }
    }
    public void upload(String name, byte[] bytes) {
        objectPath(name); requirePrivateBucket();
        call("POST", objectPath(name), config.serviceKey, config.serviceKey, bytes, "audio/wav");
    }
    public byte[] download(String name) {
        objectPath(name); requirePrivateBucket();
        String path = objectPath(name).replace("/object/", "/object/authenticated/");
        return call("GET", path, config.serviceKey, config.serviceKey, null, null);
    }
    public void delete(String name) {
        objectPath(name);
        try {
            call("DELETE", "/storage/v1/object/" + config.bucket, config.serviceKey, config.serviceKey,
                json.writeValueAsBytes(Map.of("prefixes", java.util.List.of(name))), "application/json");
        } catch (ApiException ex) { throw ex; }
        catch (Exception ex) { throw unavailable(); }
    }
    private ApiException unavailable() { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
        "SUPABASE_UNAVAILABLE", "Cloud authentication or storage is unavailable. Retry later."); }
}
