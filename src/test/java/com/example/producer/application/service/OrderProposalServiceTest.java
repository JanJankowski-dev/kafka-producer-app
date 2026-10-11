package com.example.producer.application.service;

import com.example.producer.application.port.in.OrderProposalAccepted;
import com.example.producer.application.port.in.ProposeOrderCommand;
import com.example.producer.application.port.out.AcceptOrderProposalPort;
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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderProposalServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("269ad5c6-69ea-4f88-8f21-aa4e283db26e");
    private static final UUID PROPOSAL_ID = UUID.fromString("59bd5b26-0c71-4bac-aad4-80e73c9bd9a8");

    @Test
    void shouldCreateAndStoreProposalWithDomainEvent() {
        var store = new CapturingStore();
        var ids = new ArrayDeque<>(List.of(EVENT_ID, PROPOSAL_ID));
        var service = new OrderProposalService(
                store,
                Clock.fixed(NOW, ZoneOffset.UTC),
                ids::removeFirst
        );

        var accepted = service.propose("request-1", command());

        assertThat(accepted.eventId()).isEqualTo(EVENT_ID);
        assertThat(accepted.proposalId()).isEqualTo(PROPOSAL_ID);
        assertThat(store.event.eventId()).isEqualTo(EVENT_ID);
        assertThat(store.event.occurredAt()).isEqualTo(NOW);
        assertThat(store.event.schemaVersion()).isEqualTo(1);
        assertThat(store.event.proposal().proposalId()).isEqualTo(PROPOSAL_ID);
        assertThat(store.event.proposal().portfolioId()).isEqualTo(command().portfolioId());
        assertThat(store.event.proposal().source()).isEqualTo(ProposalSource.AI_LAB);
        assertThat(store.event.proposal().maxNotional().currency()).isEqualTo(Currency.getInstance("PLN"));
    }

    @Test
    void shouldRejectExpiredProposalWithoutCallingStore() {
        var store = new CapturingStore();
        var service = new OrderProposalService(
                store,
                Clock.fixed(NOW, ZoneOffset.UTC),
                UUID::randomUUID
        );
        var expired = new ProposeOrderCommand(
                command().portfolioId(),
                command().instrumentId(),
                command().side(),
                command().maxNotional(),
                command().source(),
                command().rationale(),
                command().evidenceSources(),
                command().confidence(),
                NOW
        );

        assertThatThrownBy(() -> service.propose("request-2", expired))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("validUntil must be after occurredAt");
        assertThat(store.event).isNull();
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

    private static final class CapturingStore implements AcceptOrderProposalPort {
        private OrderProposalProposed event;

        @Override
        public OrderProposalAccepted accept(
                String idempotencyKey,
                String requestFingerprint,
                Supplier<OrderProposalProposed> newEvent
        ) {
            event = newEvent.get();
            return new OrderProposalAccepted(event.eventId(), event.proposal().proposalId());
        }
    }
}
