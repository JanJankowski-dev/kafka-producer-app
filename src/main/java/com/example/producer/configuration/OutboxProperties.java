package com.example.producer.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        boolean enabled,
        int batchSize,
        long pollDelayMs,
        Duration leaseDuration,
        int maxAttempts,
        Duration retryBaseDelay,
        Duration retryMaxDelay
) {
    public OutboxProperties {
        if (batchSize < 1) {
            throw new IllegalArgumentException("app.outbox.batch-size must be positive");
        }
        if (pollDelayMs < 1) {
            throw new IllegalArgumentException("app.outbox.poll-delay-ms must be positive");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("app.outbox.lease-duration must be positive");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("app.outbox.max-attempts must be positive");
        }
        if (retryBaseDelay == null || retryBaseDelay.isZero() || retryBaseDelay.isNegative()) {
            throw new IllegalArgumentException("app.outbox.retry-base-delay must be positive");
        }
        if (retryMaxDelay == null || retryMaxDelay.compareTo(retryBaseDelay) < 0) {
            throw new IllegalArgumentException(
                    "app.outbox.retry-max-delay must not be shorter than retry-base-delay"
            );
        }
    }
}
