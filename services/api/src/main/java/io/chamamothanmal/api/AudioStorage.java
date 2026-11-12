package io.chamamothanmal.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AudioStorage {
    public record ValidatedAudio(byte[] bytes, double seconds, String sha256) { }
    private static final Set<String> TYPES = Set.of("audio/wav", "audio/x-wav", "audio/wave");
    private final Path root;
    private final ApiProperties settings;

    public AudioStorage(ApiProperties settings) throws IOException {
        this.settings = settings;
        root = settings.uploadDir().toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (settings.maxAudioBytes() < 1 || settings.maxAudioBytes() > 5_242_880
            || settings.maxAudioSeconds() <= 0 || settings.maxAudioSeconds() > 15) {
            throw new IllegalArgumentException("Audio limits must be positive and at most 5 MiB / 15 seconds.");
        }
    }

    public ValidatedAudio validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.invalid("A non-empty audio file is required.");
        if (file.getSize() > settings.maxAudioBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "AUDIO_TOO_LARGE", "Audio exceeds the size limit.");
        }
        String type = file.getContentType();
        if (type == null || !TYPES.contains(type.toLowerCase(Locale.ROOT))) throw unsupported();
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 44 || bytes.length > settings.maxAudioBytes()
                || !new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                || !new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WAVE")) throw unsupported();
            long riffSize = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
            if (riffSize + 8 != bytes.length) throw unsupported();
            try (AudioInputStream stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
                AudioFormat format = stream.getFormat();
                if (!(format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
                    || format.getEncoding().equals(AudioFormat.Encoding.PCM_UNSIGNED))
                    || (format.getSampleSizeInBits() != 8 && format.getSampleSizeInBits() != 16)
                    || format.getChannels() < 1 || format.getChannels() > 2
                    || format.getSampleRate() < 8000 || format.getSampleRate() > 48000
                    || format.getFrameSize() <= 0 || stream.getFrameLength() <= 0) throw unsupported();
                double seconds = stream.getFrameLength() / (double) format.getFrameRate();
                if (!Double.isFinite(seconds) || seconds > settings.maxAudioSeconds()) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AUDIO_TOO_LONG", "Audio exceeds the duration limit.");
                }
                long decodedBytes = 0;
                byte[] buffer = new byte[4096];
                int read;
                while ((read = stream.read(buffer)) != -1) decodedBytes += read;
                if (decodedBytes != stream.getFrameLength() * (long) format.getFrameSize()) throw unsupported();
                return new ValidatedAudio(bytes, seconds, digest(bytes));
            }
        } catch (IOException | UnsupportedAudioFileException | IllegalArgumentException ex) {
            throw unsupported();
        }
    }

    private ApiException unsupported() {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_AUDIO", "Use a valid 8/16-bit PCM WAV, 8-48 kHz, mono or stereo.");
    }

    static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public String save(ValidatedAudio audio) {
        String name = UUID.randomUUID() + ".wav";
        Path temp = null;
        try {
            temp = Files.createTempFile(root, "incoming-", ".tmp");
            Files.write(temp, audio.bytes());
            Files.move(temp, root.resolve(name), StandardCopyOption.ATOMIC_MOVE);
            return name;
        } catch (IOException ex) {
            if (temp != null) deleteQuietly(temp);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "Audio storage is unavailable.");
        }
    }

    public Resource resource(String name) {
        Path path = safePath(name);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUDIO_UNAVAILABLE", "Audio is temporarily unavailable.");
        }
        return new FileSystemResource(path);
    }

    public void deleteStrict(String name) {
        try { Files.deleteIfExists(safePath(name)); }
        catch (IOException ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "Delete will be retried."); }
    }

    public void delete(String name) { deleteQuietly(safePath(name)); }

    private Path safePath(String name) {
        if (name == null || !name.matches("[0-9a-f-]{36}\\.wav")) throw new IllegalStateException("Invalid storage identifier.");
        Path path = root.resolve(name).normalize();
        if (!path.startsWith(root)) throw new IllegalStateException("Invalid storage path.");
        return path;
    }

    private void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { /* Reconcile orphan files during maintenance. */ }
    }
}
