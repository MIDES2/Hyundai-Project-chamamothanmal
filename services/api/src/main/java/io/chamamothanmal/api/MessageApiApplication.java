package io.chamamothanmal.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ApiProperties.class)
public class MessageApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MessageApiApplication.class, args);
    }
}
