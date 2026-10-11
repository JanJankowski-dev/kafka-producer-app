package com.example.producer.adapter.out.persistence;

import com.example.producer.application.port.in.OrderProposalAccepted;
import com.example.producer.application.port.out.AcceptOrderProposalPort;
import com.example.producer.application.port.out.StoreOrderProposalPort;
import com.example.producer.application.service.IdempotencyConflictException;
import com.example.producer.domain.OrderProposal;
import com.example.producer.domain.OrderProposalProposed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.example.producer.adapter.out.messaging.OrderProposedV1Mapper.from;
import static com.example.producer.jooq.tables.IdempotencyRequests.IDEMPOTENCY_REQUESTS;
import static com.example.producer.jooq.tables.OrderProposalEvidenceSources.ORDER_PROPOSAL_EVIDENCE_SOURCES;
import static com.example.producer.jooq.tables.OrderProposals.ORDER_PROPOSALS;
import static com.example.producer.jooq.tables.OutboxEvents.OUTBOX_EVENTS;

@Repository
public class JooqOrderProposalStore implements StoreOrderProposalPort, AcceptOrderProposalPort {
    private static final String AGGREGATE_TYPE = "ORDER_PROPOSAL";
    private static final String EVENT_TYPE = "OrderProposedV1";

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public JooqOrderProposalStore(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void store(OrderProposalProposed event) {
        insertProposal(event);
        insertEvidenceSources(event.proposal());
        insertOutboxEvent(event);
    }

    @Override
    @Transactional
    public OrderProposalAccepted accept(
            String idempotencyKey,
            String requestFingerprint,
            Supplier<OrderProposalProposed> newEvent
    ) {
        int claimed = dsl.insertInto(IDEMPOTENCY_REQUESTS)
                .set(IDEMPOTENCY_REQUESTS.IDEMPOTENCY_KEY, idempotencyKey)
                .set(IDEMPOTENCY_REQUESTS.REQUEST_FINGERPRINT, requestFingerprint)
                .onConflict(IDEMPOTENCY_REQUESTS.IDEMPOTENCY_KEY)
                .doNothing()
                .execute();

        if (claimed == 0) {
            var existing = dsl.selectFrom(IDEMPOTENCY_REQUESTS)
                    .where(IDEMPOTENCY_REQUESTS.IDEMPOTENCY_KEY.eq(idempotencyKey))
                    .fetchOne();
            if (existing == null) {
                throw new IllegalStateException("Idempotency request disappeared: " + idempotencyKey);
            }
            if (!requestFingerprint.equals(existing.get(IDEMPOTENCY_REQUESTS.REQUEST_FINGERPRINT))) {
                throw new IdempotencyConflictException();
            }
            var eventId = existing.get(IDEMPOTENCY_REQUESTS.RESPONSE_EVENT_ID);
            var proposalId = existing.get(IDEMPOTENCY_REQUESTS.RESPONSE_PROPOSAL_ID);
            if (eventId == null || proposalId == null) {
                throw new IllegalStateException("Idempotency request has no response: " + idempotencyKey);
            }
            return new OrderProposalAccepted(eventId, proposalId);
        }

        OrderProposalProposed event = newEvent.get();
        insertProposal(event);
        insertEvidenceSources(event.proposal());
        insertOutboxEvent(event);
        dsl.update(IDEMPOTENCY_REQUESTS)
                .set(IDEMPOTENCY_REQUESTS.RESPONSE_EVENT_ID, event.eventId())
                .set(IDEMPOTENCY_REQUESTS.RESPONSE_PROPOSAL_ID, event.proposal().proposalId())
                .where(IDEMPOTENCY_REQUESTS.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .execute();
        return new OrderProposalAccepted(event.eventId(), event.proposal().proposalId());
    }

    private void insertProposal(OrderProposalProposed event) {
        var proposal = event.proposal();

        dsl.insertInto(ORDER_PROPOSALS)
                .set(ORDER_PROPOSALS.PROPOSAL_ID, proposal.proposalId())
                .set(ORDER_PROPOSALS.PORTFOLIO_ID, proposal.portfolioId().value())
                .set(ORDER_PROPOSALS.INSTRUMENT_ID, proposal.instrumentId().value())
                .set(ORDER_PROPOSALS.SIDE, proposal.side().name())
                .set(ORDER_PROPOSALS.MAX_NOTIONAL, proposal.maxNotional().amount())
                .set(ORDER_PROPOSALS.CURRENCY, proposal.maxNotional().currency().getCurrencyCode())
                .set(ORDER_PROPOSALS.SOURCE, proposal.source().name())
                .set(ORDER_PROPOSALS.RATIONALE, proposal.rationale())
                .set(ORDER_PROPOSALS.CONFIDENCE, proposal.confidence().value())
                .set(ORDER_PROPOSALS.VALID_UNTIL, atUtc(proposal.validUntil()))
                .set(ORDER_PROPOSALS.PROPOSED_AT, atUtc(event.occurredAt()))
                .execute();
    }

    private void insertEvidenceSources(OrderProposal proposal) {
        List<Query> inserts = IntStream.range(0, proposal.evidenceSources().size())
                .mapToObj(position -> dsl.insertInto(ORDER_PROPOSAL_EVIDENCE_SOURCES)
                        .set(ORDER_PROPOSAL_EVIDENCE_SOURCES.PROPOSAL_ID, proposal.proposalId())
                        .set(ORDER_PROPOSAL_EVIDENCE_SOURCES.SOURCE_POSITION, (short) position)
                        .set(ORDER_PROPOSAL_EVIDENCE_SOURCES.SOURCE_URI, proposal.evidenceSources().get(position)))
                .map(Query.class::cast)
                .toList();

        if (!inserts.isEmpty()) {
            dsl.batch(inserts).execute();
        }
    }

    private void insertOutboxEvent(OrderProposalProposed event) {
        var proposal = event.proposal();

        dsl.insertInto(OUTBOX_EVENTS)
                .set(OUTBOX_EVENTS.EVENT_ID, event.eventId())
                .set(OUTBOX_EVENTS.AGGREGATE_ID, proposal.proposalId())
                .set(OUTBOX_EVENTS.AGGREGATE_TYPE, AGGREGATE_TYPE)
                .set(OUTBOX_EVENTS.PARTITION_KEY, proposal.portfolioId().value().toString())
                .set(OUTBOX_EVENTS.EVENT_TYPE, EVENT_TYPE)
                .set(OUTBOX_EVENTS.SCHEMA_VERSION, event.schemaVersion())
                .set(OUTBOX_EVENTS.PAYLOAD, serialize(event))
                .set(OUTBOX_EVENTS.OCCURRED_AT, atUtc(event.occurredAt()))
                .execute();
    }

    private JSONB serialize(OrderProposalProposed event) {
        try {
            return JSONB.valueOf(objectMapper.writeValueAsString(from(event)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize order proposal event " + event.eventId(),
                    exception
            );
        }
    }

    private OffsetDateTime atUtc(java.time.Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
