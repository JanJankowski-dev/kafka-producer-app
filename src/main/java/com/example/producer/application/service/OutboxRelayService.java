package com.example.producer.application.service;

import com.example.producer.application.port.out.OutboxRepositoryPort;
import com.example.producer.application.port.out.PendingOutboxEvent;
import com.example.producer.application.port.out.PublishOutboxEventPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionException;

public class OutboxRelayService {
    private static final int MAX_ERROR_LENGTH = 2000;

    private final OutboxRepositoryPort repository;
    private final PublishOutboxEventPort publisher;
    private final Clock clock;
    private final int batchSize;
    private final Duration leaseDuration;
    private final int maxAttempts;
    private final Duration retryBaseDelay;
    private final Duration retryMaxDelay;

    public OutboxRelayService(
            OutboxRepositoryPort repository,
            PublishOutboxEventPort publisher,
            Clock clock,
            int batchSize,
            Duration leaseDuration,
            int maxAttempts,
            Duration retryBaseDelay,
            Duration retryMaxDelay
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = batchSize;
        this.leaseDuration = leaseDuration;
        this.maxAttempts = maxAttempts;
        this.retryBaseDelay = retryBaseDelay;
        this.retryMaxDelay = retryMaxDelay;
    }

    public int relayBatch() {
        Instant now = clock.instant();
        var events = repository.claimPending(batchSize, now, now.plus(leaseDuration));
        events.forEach(this::publish);
        return events.size();
    }

    private void publish(PendingOutboxEvent event) {
        try {
            publisher.publish(event).toCompletableFuture().join();
            repository.markPublished(event.eventId(), event.claimId(), clock.instant());
        } catch (RuntimeException exception) {
            Throwable cause = unwrap(exception);
            boolean dead = event.attempts() >= maxAttempts;
            Instant nextAttemptAt = dead
                    ? clock.instant()
                    : clock.instant().plus(retryDelay(event.attempts()));
            repository.markFailed(
                    event.eventId(),
                    event.claimId(),
                    nextAttemptAt,
                    errorDescription(cause),
                    dead
            );
        }
    }

    private Duration retryDelay(int attempts) {
        Duration delay = retryBaseDelay;
        for (int attempt = 1; attempt < attempts && delay.compareTo(retryMaxDelay) < 0; attempt++) {
            Duration doubled = delay.multipliedBy(2);
            delay = doubled.compareTo(retryMaxDelay) > 0 ? retryMaxDelay : doubled;
        }
        return delay;
    }

    private Throwable unwrap(Throwable exception) {
        return exception instanceof CompletionException && exception.getCause() != null
                ? exception.getCause()
                : exception;
    }

    private String errorDescription(Throwable exception) {
        String message = exception.getMessage();
        String description = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return description.length() <= MAX_ERROR_LENGTH
                ? description
                : description.substring(0, MAX_ERROR_LENGTH);
    }
}
