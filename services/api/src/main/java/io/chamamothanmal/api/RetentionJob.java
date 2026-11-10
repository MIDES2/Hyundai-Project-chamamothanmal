package io.chamamothanmal.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.cleanup-enabled",havingValue="true",matchIfMissing=true)
public class RetentionJob {
    private final FamilyService service;
    public RetentionJob(FamilyService service) { this.service=service; }
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void cleanup() { service.sweep(); }
}
