package io.chamamothanmal.api;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Private object bytes are proxied only after API authorization; no public/signed URL is returned. */
@Component
public class VoiceBlobStore {
    private final AudioStorage local;
    private final SupabaseGateway cloud;
    private final boolean remote;
    public VoiceBlobStore(AudioStorage local, SupabaseGateway cloud, CloudSettings settings) {
        this.local = local; this.cloud = cloud; remote = settings.storageMode.equals("supabase");
    }
    public String save(AudioStorage.ValidatedAudio audio) {
        if (!remote) return local.save(audio);
        String name = UUID.randomUUID() + ".wav";
        cloud.upload(name, audio.bytes()); return name;
    }
    public byte[] read(String name) {
        if (remote) return cloud.download(name);
        try { return local.resource(name).getContentAsByteArray(); }
        catch (java.io.IOException ex) { throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
            "AUDIO_UNAVAILABLE", "Audio is unavailable."); }
    }
    public void delete(String name) {
        if (remote) cloud.delete(name);
        else local.deleteStrict(name);
    }
}
