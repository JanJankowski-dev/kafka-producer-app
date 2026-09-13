package com.example.producer.application.service;

import com.example.producer.application.port.in.ProposeOrderCommand;
import com.example.producer.application.port.out.PublishOrderProposalPort;
import com.example.producer.domain.Confidence;
import com.example.producer.domain.InstrumentId;
import com.example.producer.domain.NotionalLimit;
import com.example.producer.domain.OrderProposalProposed;
import com.example.producer.domain.OrderSide;
import com.example.producer.domain.PortfolioId;
import com.example.producer.domain.ProposalSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class OrderProposalServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("269ad5c6-69ea-4f88-8f21-aa4e283db26e");
    private static final UUID PROPOSAL_ID = UUID.fromString("59bd5b26-0c71-4bac-aad4-80e73c9bd9a8");

    @Test
    void shouldCreateProposalAndPublishDomainEvent() {
        var publisher = new CapturingPublisher();
        var ids = new ArrayDeque<>(List.of(EVENT_ID, PROPOSAL_ID));
        var service = new OrderProposalService(
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                ids::removeFirst
        );

        var accepted = service.propose(command()).toCompletableFuture().join();

        assertThat(accepted.eventId()).isEqualTo(EVENT_ID);
        assertThat(accepted.proposalId()).isEqualTo(PROPOSAL_ID);
        assertThat(publisher.event.eventId()).isEqualTo(EVENT_ID);
        assertThat(publisher.event.occurredAt()).isEqualTo(NOW);
        assertThat(publisher.event.schemaVersion()).isEqualTo(1);
        assertThat(publisher.event.proposal().proposalId()).isEqualTo(PROPOSAL_ID);
        assertThat(publisher.event.proposal().portfolioId()).isEqualTo(command().portfolioId());
        assertThat(publisher.event.proposal().strategySource()).isEqualTo(ProposalSource.AI_LAB);
        assertThat(publisher.event.proposal().maxNotional().currency()).isEqualTo(Currency.getInstance("PLN"));
    }

    @Test
    void shouldRejectExpiredProposalWithoutCallingPublisher() {
        var publisher = new CapturingPublisher();
        var service = new OrderProposalService(
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                UUID::randomUUID
        );
        var expired = new ProposeOrderCommand(
                command().portfolioId(),
                command().instrumentId(),
                command().side(),
                command().maxNotional(),
                command().strategySource(),
                command().rationale(),
                command().evidenceSources(),
                command().confidence(),
                NOW
        );

        var future = service.propose(expired).toCompletableFuture();

        assertThat(future).isCompletedExceptionally();
        assertThat(publisher.event).isNull();
    }

    private ProposeOrderCommand command() {
        return new ProposeOrderCommand(
                new PortfolioId(UUID.fromString("256f29f4-24d3-4737-88e1-6414a93a3fdc")),
                new InstrumentId(UUID.fromString("16377765-cc93-4147-a59c-8e8ac634a073")),
                OrderSide.BUY,
                new NotionalLimit(new BigDecimal("123.45"), Currency.getInstance("PLN")),
                ProposalSource.AI_LAB,
                "Positive earnings revision",
                List.of("https://example.com/report"),
                new Confidence(new BigDecimal("0.72")),
                Instant.parse("2026-09-13T10:00:00Z")
        );
    }

    private static final class CapturingPublisher implements PublishOrderProposalPort {
        private OrderProposalProposed event;

        @Override
        public CompletableFuture<Void> publish(OrderProposalProposed event) {
            this.event = event;
            return CompletableFuture.completedFuture(null);
        }
    }
}
