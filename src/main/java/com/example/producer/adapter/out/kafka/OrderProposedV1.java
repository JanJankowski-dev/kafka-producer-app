package com.example.producer.adapter.out.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderProposedV1(
        UUID eventId,
        UUID proposalId,
        Instant occurredAt,
        int schemaVersion,
        UUID portfolioId,
        UUID instrumentId,
        String side,
        BigDecimal maxNotional,
        String currency,
        String strategySource,
        String rationale,
        List<String> evidenceSources,
        BigDecimal confidence,
        Instant validUntil
) {
    public OrderProposedV1 {
        evidenceSources = List.copyOf(evidenceSources);
    }
}
