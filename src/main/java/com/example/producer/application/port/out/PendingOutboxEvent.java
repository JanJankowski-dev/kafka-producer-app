package com.example.producer.application.port.out;

import java.util.UUID;

public record PendingOutboxEvent(
        UUID eventId,
        UUID claimId,
        String partitionKey,
        String eventType,
        int schemaVersion,
        String payload,
        int attempts
) {
}
