package com.example.producer.application.service;

import com.example.producer.application.port.in.OrderProposalAccepted;
import com.example.producer.application.port.in.ProposeOrderCommand;
import com.example.producer.application.port.in.ProposeOrderUseCase;
import com.example.producer.application.port.out.AcceptOrderProposalPort;
import com.example.producer.domain.OrderProposal;
import com.example.producer.domain.OrderProposalProposed;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

public class OrderProposalService implements ProposeOrderUseCase {
    private static final int SCHEMA_VERSION = 1;

    private final AcceptOrderProposalPort store;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    public OrderProposalService(
            AcceptOrderProposalPort store,
            Clock clock,
            Supplier<UUID> idGenerator
    ) {
        this.store = store;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public OrderProposalAccepted propose(String idempotencyKey, ProposeOrderCommand command) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1-128 safe ASCII characters");
        }
        return store.accept(
                idempotencyKey,
                OrderProposalFingerprint.from(command),
                () -> newEvent(command)
        );
    }

    private OrderProposalProposed newEvent(ProposeOrderCommand command) {
        Instant occurredAt = clock.instant();
        if (!command.validUntil().isAfter(occurredAt)) {
            throw new IllegalArgumentException("validUntil must be after occurredAt");
        }

        UUID eventId = idGenerator.get();
        UUID proposalId = idGenerator.get();
        var proposal = new OrderProposal(
                proposalId,
                command.portfolioId(),
                command.instrumentId(),
                command.side(),
                command.maxNotional(),
                command.source(),
                command.rationale(),
                command.evidenceSources(),
                command.confidence(),
                command.validUntil()
        );
        return new OrderProposalProposed(
                eventId,
                occurredAt,
                SCHEMA_VERSION,
                proposal
        );
    }
}
