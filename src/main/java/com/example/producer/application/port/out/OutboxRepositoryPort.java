package com.example.producer.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepositoryPort {
    List<PendingOutboxEvent> claimPending(
            int batchSize,
            Instant availableAt,
            Instant claimedUntil
    );

    void markPublished(UUID eventId, UUID claimId, Instant publishedAt);

    void markFailed(
            UUID eventId,
            UUID claimId,
            Instant nextAttemptAt,
            String lastError,
            boolean dead
    );
}
