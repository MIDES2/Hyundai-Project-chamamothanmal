package io.chamamothanmal.api;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record ApiProperties(String environment, Path uploadDir, long maxAudioBytes,
                            double maxAudioSeconds, ZoneId timezone, List<String> corsOrigins,
                            String senderToken, String recipientToken, String otherRecipientToken) {
}
