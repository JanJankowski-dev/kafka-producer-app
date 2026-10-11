package com.example.producer.adapter.out.persistence;

import com.example.producer.application.port.in.ProposeOrderCommand;
import com.example.producer.application.port.in.ProposeOrderUseCase;
import com.example.producer.application.port.out.PendingOutboxEvent;
import com.example.producer.application.port.out.PublishOutboxEventPort;
import com.example.producer.application.service.OutboxRelayService;
import com.example.producer.configuration.PostgresIntegrationTest;
import com.example.producer.domain.Confidence;
import com.example.producer.domain.InstrumentId;
import com.example.producer.domain.NotionalLimit;
import com.example.producer.domain.OrderSide;
import com.example.producer.domain.PortfolioId;
import com.example.producer.domain.ProposalSource;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.example.producer.jooq.tables.IdempotencyRequests.IDEMPOTENCY_REQUESTS;
import static com.example.producer.jooq.tables.OrderProposalEvidenceSources.ORDER_PROPOSAL_EVIDENCE_SOURCES;
import static com.example.producer.jooq.tables.OrderProposals.ORDER_PROPOSALS;
import static com.example.producer.jooq.tables.OutboxEvents.OUTBOX_EVENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@PostgresIntegrationTest
class OutboxRelayFlowTest {
    @Autowired
    private ProposeOrderUseCase proposeOrder;

    @Autowired
    private OutboxRelayService relay;

    @Autowired
    private DSLContext dsl;

    @MockitoBean
    private PublishOutboxEventPort publisher;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(IDEMPOTENCY_REQUESTS).execute();
        dsl.deleteFrom(ORDER_PROPOSAL_EVIDENCE_SOURCES).execute();
        dsl.deleteFrom(OUTBOX_EVENTS).execute();
        dsl.deleteFrom(ORDER_PROPOSALS).execute();
    }

    @Test
    void shouldStoreProposalAndPublishItsOutboxEvent() {
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));

        var accepted = proposeOrder.propose(UUID.randomUUID().toString(), command());

        var pending = dsl.selectFrom(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.EVENT_ID.eq(accepted.eventId()))
                .fetchSingle();
        assertThat(pending.getStatus()).isEqualTo("PENDING");
        assertThat(pending.getAttempts()).isZero();

        int processed = relay.relayBatch();

        assertThat(processed).isOne();
        var publishedEvent = ArgumentCaptor.forClass(PendingOutboxEvent.class);
        verify(publisher).publish(publishedEvent.capture());
        assertThat(publishedEvent.getValue().eventId()).isEqualTo(accepted.eventId());
        assertThat(publishedEvent.getValue().attempts()).isOne();

        var published = dsl.selectFrom(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.EVENT_ID.eq(accepted.eventId()))
                .fetchSingle();
        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getClaimId()).isNull();
        assertThat(published.getClaimedUntil()).isNull();
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
                Instant.parse("2099-09-13T10:00:00Z")
        );
    }
}
