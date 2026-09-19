package com.example.producer.adapter.out.persistence;

import com.example.producer.application.port.out.StoreOrderProposalPort;
import com.example.producer.configuration.PostgresIntegrationTest;
import com.example.producer.domain.Confidence;
import com.example.producer.domain.InstrumentId;
import com.example.producer.domain.NotionalLimit;
import com.example.producer.domain.OrderProposal;
import com.example.producer.domain.OrderProposalProposed;
import com.example.producer.domain.OrderSide;
import com.example.producer.domain.PortfolioId;
import com.example.producer.domain.ProposalSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static com.example.producer.jooq.tables.OrderProposalEvidenceSources.ORDER_PROPOSAL_EVIDENCE_SOURCES;
import static com.example.producer.jooq.tables.OrderProposals.ORDER_PROPOSALS;
import static com.example.producer.jooq.tables.OutboxEvents.OUTBOX_EVENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
class JooqOrderProposalStoreTest {
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-19T08:00:00Z");

    @Autowired
    private StoreOrderProposalPort store;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldStoreProposalEvidenceSourcesAndPendingOutboxEvent() throws Exception {
        var event = event(UUID.randomUUID(), UUID.randomUUID());

        store.store(event);

        var proposal = dsl.selectFrom(ORDER_PROPOSALS)
                .where(ORDER_PROPOSALS.PROPOSAL_ID.eq(event.proposal().proposalId()))
                .fetchSingle();
        var evidenceSources = dsl.select(ORDER_PROPOSAL_EVIDENCE_SOURCES.SOURCE_URI)
                .from(ORDER_PROPOSAL_EVIDENCE_SOURCES)
                .where(ORDER_PROPOSAL_EVIDENCE_SOURCES.PROPOSAL_ID.eq(event.proposal().proposalId()))
                .orderBy(ORDER_PROPOSAL_EVIDENCE_SOURCES.SOURCE_POSITION)
                .fetch(ORDER_PROPOSAL_EVIDENCE_SOURCES.SOURCE_URI);
        var outbox = dsl.selectFrom(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.EVENT_ID.eq(event.eventId()))
                .fetchSingle();
        var payload = objectMapper.readTree(outbox.get(OUTBOX_EVENTS.PAYLOAD).data());

        assertThat(proposal.get(ORDER_PROPOSALS.PORTFOLIO_ID))
                .isEqualTo(event.proposal().portfolioId().value());
        assertThat(proposal.get(ORDER_PROPOSALS.MAX_NOTIONAL))
                .isEqualByComparingTo(event.proposal().maxNotional().amount());
        assertThat(proposal.get(ORDER_PROPOSALS.STATUS)).isEqualTo("PROPOSED");
        assertThat(proposal.get(ORDER_PROPOSALS.PROPOSED_AT))
                .isEqualTo(OCCURRED_AT.atOffset(ZoneOffset.UTC));
        assertThat(evidenceSources).containsExactly(
                "https://example.com/earnings",
                "https://example.com/valuation"
        );
        assertThat(outbox.get(OUTBOX_EVENTS.AGGREGATE_ID))
                .isEqualTo(event.proposal().proposalId());
        assertThat(outbox.get(OUTBOX_EVENTS.PARTITION_KEY))
                .isEqualTo(event.proposal().portfolioId().value().toString());
        assertThat(outbox.get(OUTBOX_EVENTS.EVENT_TYPE)).isEqualTo("OrderProposedV1");
        assertThat(outbox.get(OUTBOX_EVENTS.STATUS)).isEqualTo("PENDING");
        assertThat(outbox.get(OUTBOX_EVENTS.ATTEMPTS)).isZero();
        assertThat(payload.get("eventId").asText()).isEqualTo(event.eventId().toString());
        assertThat(payload.get("proposalId").asText())
                .isEqualTo(event.proposal().proposalId().toString());
        assertThat(payload.get("evidenceSources")).hasSize(2);
    }

    @Test
    void shouldRollbackProposalAndEvidenceWhenOutboxInsertFails() {
        UUID duplicatedEventId = UUID.randomUUID();
        var stored = event(duplicatedEventId, UUID.randomUUID());
        var rejected = event(duplicatedEventId, UUID.randomUUID());
        store.store(stored);

        assertThatThrownBy(() -> store.store(rejected))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(dsl.fetchExists(
                dsl.selectOne()
                        .from(ORDER_PROPOSALS)
                        .where(ORDER_PROPOSALS.PROPOSAL_ID.eq(rejected.proposal().proposalId()))
        )).isFalse();
        assertThat(dsl.fetchExists(
                dsl.selectOne()
                        .from(ORDER_PROPOSAL_EVIDENCE_SOURCES)
                        .where(ORDER_PROPOSAL_EVIDENCE_SOURCES.PROPOSAL_ID.eq(
                                rejected.proposal().proposalId()
                        ))
        )).isFalse();
        assertThat(dsl.fetchCount(
                dsl.selectFrom(OUTBOX_EVENTS)
                        .where(OUTBOX_EVENTS.EVENT_ID.eq(duplicatedEventId))
        )).isEqualTo(1);
    }

    private OrderProposalProposed event(UUID eventId, UUID proposalId) {
        var proposal = new OrderProposal(
                proposalId,
                new PortfolioId(UUID.randomUUID()),
                new InstrumentId(UUID.randomUUID()),
                OrderSide.BUY,
                new NotionalLimit(new BigDecimal("123.45"), Currency.getInstance("PLN")),
                ProposalSource.AI_LAB,
                "Positive earnings revision",
                List.of(
                        "https://example.com/earnings",
                        "https://example.com/valuation"
                ),
                new Confidence(new BigDecimal("0.72")),
                OCCURRED_AT.plusSeconds(3600)
        );
        return new OrderProposalProposed(eventId, OCCURRED_AT, 1, proposal);
    }
}
