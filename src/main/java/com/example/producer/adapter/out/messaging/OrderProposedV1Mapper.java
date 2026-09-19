package com.example.producer.adapter.out.messaging;

import com.example.producer.domain.OrderProposalProposed;

public final class OrderProposedV1Mapper {
    private OrderProposedV1Mapper() {
    }

    public static OrderProposedV1 from(OrderProposalProposed event) {
        var proposal = event.proposal();
        return new OrderProposedV1(
                event.eventId(),
                proposal.proposalId(),
                event.occurredAt(),
                event.schemaVersion(),
                proposal.portfolioId().value(),
                proposal.instrumentId().value(),
                proposal.side().name(),
                proposal.maxNotional().amount(),
                proposal.maxNotional().currency().getCurrencyCode(),
                proposal.source().name(),
                proposal.rationale(),
                proposal.evidenceSources(),
                proposal.confidence().value(),
                proposal.validUntil()
        );
    }
}
