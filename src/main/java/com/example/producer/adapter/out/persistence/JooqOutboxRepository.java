package com.example.producer.adapter.out.persistence;

import com.example.producer.application.port.out.OutboxRepositoryPort;
import com.example.producer.application.port.out.PendingOutboxEvent;
import com.example.producer.jooq.tables.records.OutboxEventsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.example.producer.jooq.tables.OutboxEvents.OUTBOX_EVENTS;

@Repository
public class JooqOutboxRepository implements OutboxRepositoryPort {
    private static final String PENDING = "PENDING";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String DEAD = "DEAD";

    private final DSLContext dsl;

    public JooqOutboxRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public List<PendingOutboxEvent> claimPending(
            int batchSize,
            Instant availableAt,
            Instant claimedUntil
    ) {
        var availableAtUtc = atUtc(availableAt);
        var claimId = UUID.randomUUID();
        var eventIds = dsl.select(OUTBOX_EVENTS.EVENT_ID)
                .from(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.STATUS.eq(PENDING))
                .and(OUTBOX_EVENTS.NEXT_ATTEMPT_AT.le(availableAtUtc))
                .and(OUTBOX_EVENTS.CLAIMED_UNTIL.isNull()
                        .or(OUTBOX_EVENTS.CLAIMED_UNTIL.le(availableAtUtc)))
                .orderBy(OUTBOX_EVENTS.OCCURRED_AT, OUTBOX_EVENTS.EVENT_ID)
                .limit(batchSize)
                .forUpdate()
                .skipLocked()
                .fetch(OUTBOX_EVENTS.EVENT_ID);

        if (eventIds.isEmpty()) {
            return List.of();
        }

        dsl.update(OUTBOX_EVENTS)
                .set(OUTBOX_EVENTS.CLAIM_ID, claimId)
                .set(OUTBOX_EVENTS.CLAIMED_UNTIL, atUtc(claimedUntil))
                .set(OUTBOX_EVENTS.ATTEMPTS, OUTBOX_EVENTS.ATTEMPTS.plus(1))
                .where(OUTBOX_EVENTS.EVENT_ID.in(eventIds))
                .execute();

        return dsl.selectFrom(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.CLAIM_ID.eq(claimId))
                .orderBy(OUTBOX_EVENTS.OCCURRED_AT, OUTBOX_EVENTS.EVENT_ID)
                .fetch(this::toPendingEvent);
    }

    @Override
    @Transactional
    public void markPublished(UUID eventId, UUID claimId, Instant publishedAt) {
        dsl.update(OUTBOX_EVENTS)
                .set(OUTBOX_EVENTS.STATUS, PUBLISHED)
                .set(OUTBOX_EVENTS.PUBLISHED_AT, atUtc(publishedAt))
                .setNull(OUTBOX_EVENTS.LAST_ERROR)
                .setNull(OUTBOX_EVENTS.CLAIM_ID)
                .setNull(OUTBOX_EVENTS.CLAIMED_UNTIL)
                .where(OUTBOX_EVENTS.EVENT_ID.eq(eventId))
                .and(OUTBOX_EVENTS.CLAIM_ID.eq(claimId))
                .and(OUTBOX_EVENTS.STATUS.eq(PENDING))
                .execute();
    }

    @Override
    @Transactional
    public void markFailed(
            UUID eventId,
            UUID claimId,
            Instant nextAttemptAt,
            String lastError,
            boolean dead
    ) {
        dsl.update(OUTBOX_EVENTS)
                .set(OUTBOX_EVENTS.STATUS, dead ? DEAD : PENDING)
                .set(OUTBOX_EVENTS.NEXT_ATTEMPT_AT, atUtc(nextAttemptAt))
                .set(OUTBOX_EVENTS.LAST_ERROR, lastError)
                .setNull(OUTBOX_EVENTS.CLAIM_ID)
                .setNull(OUTBOX_EVENTS.CLAIMED_UNTIL)
                .where(OUTBOX_EVENTS.EVENT_ID.eq(eventId))
                .and(OUTBOX_EVENTS.CLAIM_ID.eq(claimId))
                .and(OUTBOX_EVENTS.STATUS.eq(PENDING))
                .execute();
    }

    private PendingOutboxEvent toPendingEvent(OutboxEventsRecord record) {
        return new PendingOutboxEvent(
                record.getEventId(),
                record.getClaimId(),
                record.getPartitionKey(),
                record.getEventType(),
                record.getSchemaVersion(),
                record.getPayload().data(),
                record.getAttempts()
        );
    }

    private OffsetDateTime atUtc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
