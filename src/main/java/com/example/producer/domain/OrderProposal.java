package com.example.producer.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrderProposal(
        UUID proposalId,
        PortfolioId portfolioId,
        InstrumentId instrumentId,
        OrderSide side,
        NotionalLimit maxNotional,
        ProposalSource strategySource,
        String rationale,
        List<String> evidenceSources,
        Confidence confidence,
        Instant validUntil
) {
    public OrderProposal {
        Objects.requireNonNull(proposalId, "proposalId must not be null");
        Objects.requireNonNull(portfolioId, "portfolioId must not be null");
        Objects.requireNonNull(instrumentId, "instrumentId must not be null");
        Objects.requireNonNull(side, "side must not be null");
        Objects.requireNonNull(maxNotional, "maxNotional must not be null");
        Objects.requireNonNull(strategySource, "strategySource must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");
        Objects.requireNonNull(evidenceSources, "evidenceSources must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(validUntil, "validUntil must not be null");

        evidenceSources = List.copyOf(evidenceSources);
    }
}
