package com.example.producer.application.service;

import com.example.producer.application.port.out.OutboxRepositoryPort;
import com.example.producer.application.port.out.PendingOutboxEvent;
import com.example.producer.application.port.out.PublishOutboxEventPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRelayServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");

    @Test
    void shouldMarkEventAsPublishedAfterBrokerAcknowledgement() {
        var repository = new RecordingRepository(event(1));
        PublishOutboxEventPort publisher = ignored -> CompletableFuture.completedFuture(null);
        var relay = relay(repository, publisher, 5);

        int processed = relay.relayBatch();

        assertThat(processed).isOne();
        assertThat(repository.publishedEventId).isEqualTo(repository.event.eventId());
        assertThat(repository.failedEventId).isNull();
    }

    @Test
    void shouldScheduleRetryWithExponentialBackoff() {
        var repository = new RecordingRepository(event(2));
        PublishOutboxEventPort publisher = ignored ->
                CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"));
        var relay = relay(repository, publisher, 5);

        relay.relayBatch();

        assertThat(repository.failedEventId).isEqualTo(repository.event.eventId());
        assertThat(repository.dead).isFalse();
        assertThat(repository.nextAttemptAt).isEqualTo(NOW.plusSeconds(4));
        assertThat(repository.lastError).isEqualTo("IllegalStateException: broker unavailable");
    }

    @Test
    void shouldMoveEventToDeadStateAfterLastAttempt() {
        var repository = new RecordingRepository(event(5));
        PublishOutboxEventPort publisher = ignored ->
                CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"));
        var relay = relay(repository, publisher, 5);

        relay.relayBatch();

        assertThat(repository.dead).isTrue();
        assertThat(repository.nextAttemptAt).isEqualTo(NOW);
    }

    private OutboxRelayService relay(
            OutboxRepositoryPort repository,
            PublishOutboxEventPort publisher,
            int maxAttempts
    ) {
        return new OutboxRelayService(
                repository,
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                10,
                Duration.ofSeconds(30),
                maxAttempts,
                Duration.ofSeconds(2),
                Duration.ofMinutes(1)
        );
    }

    private PendingOutboxEvent event(int attempts) {
        return new PendingOutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                "OrderProposedV1",
                1,
                "{}",
                attempts
        );
    }

    private static final class RecordingRepository implements OutboxRepositoryPort {
        private final PendingOutboxEvent event;
        private UUID publishedEventId;
        private UUID failedEventId;
        private Instant nextAttemptAt;
        private String lastError;
        private boolean dead;

        private RecordingRepository(PendingOutboxEvent event) {
            this.event = event;
        }

        @Override
        public List<PendingOutboxEvent> claimPending(
                int batchSize,
                Instant availableAt,
                Instant claimedUntil
        ) {
            return List.of(event);
        }

        @Override
        public void markPublished(UUID eventId, UUID claimId, Instant publishedAt) {
            this.publishedEventId = eventId;
        }

        @Override
        public void markFailed(
                UUID eventId,
                UUID claimId,
                Instant nextAttemptAt,
                String lastError,
                boolean dead
        ) {
            this.failedEventId = eventId;
            this.nextAttemptAt = nextAttemptAt;
            this.lastError = lastError;
            this.dead = dead;
        }
    }
}
