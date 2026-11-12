package io.chamamothanmal.api;

import java.net.URI;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class CloudSettings {
    public final String authMode, storageMode, url, publishableKey, serviceKey, bucket;
    public CloudSettings(Environment env) {
        authMode = env.getProperty("app.auth-mode", "local");
        storageMode = env.getProperty("app.storage-mode", "local");
        url = env.getProperty("app.supabase-url", "").replaceAll("/+$", "");
        publishableKey = env.getProperty("app.supabase-publishable-key", "");
        serviceKey = env.getProperty("app.supabase-service-role-key", "");
        bucket = env.getProperty("app.supabase-bucket", "voices");
        if (!java.util.Set.of("local", "supabase").contains(authMode)
            || !java.util.Set.of("local", "supabase").contains(storageMode))
            throw new IllegalArgumentException("Use local or supabase mode.");
        if (authMode.equals("local") && !storageMode.equals("local"))
            throw new IllegalArgumentException("Local test accounts cannot access cloud storage.");
        if (authMode.equals("supabase") || storageMode.equals("supabase")) {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || !uri.getPath().isEmpty() || uri.getQuery() != null || uri.getFragment() != null)
                throw new IllegalArgumentException("Configure an HTTPS Supabase project origin.");
        }
        if (authMode.equals("supabase") && publishableKey.isBlank())
            throw new IllegalArgumentException("Configure SUPABASE_PUBLISHABLE_KEY on the server.");
        if (storageMode.equals("supabase") && (serviceKey.isBlank() || !bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")))
            throw new IllegalArgumentException("Configure server-only storage credentials and private bucket.");
    }
}
