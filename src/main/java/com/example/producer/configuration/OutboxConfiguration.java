package com.example.producer.configuration;

import com.example.producer.application.port.out.OutboxRepositoryPort;
import com.example.producer.application.port.out.PublishOutboxEventPort;
import com.example.producer.application.service.OutboxRelayService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfiguration {
    @Bean
    OutboxRelayService outboxRelayService(
            OutboxRepositoryPort repository,
            PublishOutboxEventPort publisher,
            Clock clock,
            OutboxProperties properties
    ) {
        return new OutboxRelayService(
                repository,
                publisher,
                clock,
                properties.batchSize(),
                properties.leaseDuration(),
                properties.maxAttempts(),
                properties.retryBaseDelay(),
                properties.retryMaxDelay()
        );
    }
}
