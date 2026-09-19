package com.example.producer.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.example.producer.jooq.tables.OrderProposals.ORDER_PROPOSALS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
class OrderProposalConstraintsTest {

    @Autowired
    private DSLContext dsl;

    @Test
    void shouldUseProposedAsDefaultStatus() {
        UUID proposalId = insertValidProposal();

        String status = dsl.select(ORDER_PROPOSALS.STATUS)
                .from(ORDER_PROPOSALS)
                .where(ORDER_PROPOSALS.PROPOSAL_ID.eq(proposalId))
                .fetchSingle(ORDER_PROPOSALS.STATUS);

        assertThat(status).isEqualTo("PROPOSED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01"})
    void shouldRejectNonPositiveMaxNotional(String maxNotional) {
        assertThatThrownBy(() -> insertProposal(UUID.randomUUID(), new BigDecimal(maxNotional)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "1.01"})
    void shouldRejectConfidenceOutOfScope(String confidence) {
        assertThatThrownBy(() ->
                dsl.insertInto(ORDER_PROPOSALS)
                .set(ORDER_PROPOSALS.PROPOSAL_ID, UUID.randomUUID())
                .set(ORDER_PROPOSALS.PORTFOLIO_ID, UUID.randomUUID())
                .set(ORDER_PROPOSALS.INSTRUMENT_ID, UUID.randomUUID())
                .set(ORDER_PROPOSALS.SIDE, "BUY")
                .set(ORDER_PROPOSALS.MAX_NOTIONAL, BigDecimal.ONE)
                .set(ORDER_PROPOSALS.CURRENCY, "PLN")
                .set(ORDER_PROPOSALS.SOURCE, "AI_LAB")
                .set(ORDER_PROPOSALS.RATIONALE, "Valid proposal used by an integration test")
                .set(ORDER_PROPOSALS.CONFIDENCE, new BigDecimal(confidence))
                .set(ORDER_PROPOSALS.VALID_UNTIL, OffsetDateTime.now().plusHours(1))
                .set(ORDER_PROPOSALS.PROPOSED_AT, OffsetDateTime.now())
                .execute())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    private UUID insertValidProposal() {
        UUID proposalId = UUID.randomUUID();
        insertProposal(proposalId, new BigDecimal("1000.00"));
        return proposalId;
    }

    private void insertProposal(UUID proposalId, BigDecimal maxNotional) {
        OffsetDateTime proposedAt = OffsetDateTime.now();

        dsl.insertInto(ORDER_PROPOSALS)
                .set(ORDER_PROPOSALS.PROPOSAL_ID, proposalId)
                .set(ORDER_PROPOSALS.PORTFOLIO_ID, UUID.randomUUID())
                .set(ORDER_PROPOSALS.INSTRUMENT_ID, UUID.randomUUID())
                .set(ORDER_PROPOSALS.SIDE, "BUY")
                .set(ORDER_PROPOSALS.MAX_NOTIONAL, maxNotional)
                .set(ORDER_PROPOSALS.CURRENCY, "PLN")
                .set(ORDER_PROPOSALS.SOURCE, "AI_LAB")
                .set(ORDER_PROPOSALS.RATIONALE, "Valid proposal used by an integration test")
                .set(ORDER_PROPOSALS.CONFIDENCE, new BigDecimal("0.80"))
                .set(ORDER_PROPOSALS.VALID_UNTIL, proposedAt.plusHours(1))
                .set(ORDER_PROPOSALS.PROPOSED_AT, proposedAt)
                .execute();
    }
}
