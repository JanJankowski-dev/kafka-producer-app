package com.example.producer.application.service;

import com.example.producer.application.port.in.OrderProposalAccepted;
import com.example.producer.application.port.in.ProposeOrderCommand;
import com.example.producer.application.port.in.ProposeOrderUseCase;
import com.example.producer.application.port.out.PublishOrderProposalPort;
import com.example.producer.domain.OrderProposal;
import com.example.producer.domain.OrderProposalProposed;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public class OrderProposalService implements ProposeOrderUseCase {
    private static final int SCHEMA_VERSION = 1;

    private final PublishOrderProposalPort publisher;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    public OrderProposalService(
            PublishOrderProposalPort publisher,
            Clock clock,
            Supplier<UUID> idGenerator
    ) {
        this.publisher = publisher;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public CompletionStage<OrderProposalAccepted> propose(ProposeOrderCommand command) {
        try {
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
                    command.strategySource(),
                    command.rationale(),
                    command.evidenceSources(),
                    command.confidence(),
                    command.validUntil()
            );
            var event = new OrderProposalProposed(
                    eventId,
                    occurredAt,
                    SCHEMA_VERSION,
                    proposal
            );

            return publisher.publish(event)
                    .thenApply(ignored -> new OrderProposalAccepted(eventId, proposalId));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
