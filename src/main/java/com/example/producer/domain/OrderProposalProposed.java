package com.example.producer.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderProposalProposed(
        UUID eventId,
        Instant occurredAt,
        int schemaVersion,
        OrderProposal proposal
) {
    public OrderProposalProposed {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(proposal, "proposal must not be null");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
    }
}
