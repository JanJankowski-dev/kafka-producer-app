package com.example.producer.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
class LiquibaseMigrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldApplyDatabaseMigrations() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('order_proposals', 'order_proposal_evidence_sources')
                """, Integer.class);

        Integer changeSetCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM databasechangelog
                WHERE id IN ('001-create-order-proposals', '002-create-outbox-events')
                  AND author = 'janjankowski'
                """, Integer.class);

        Integer outboxTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'outbox_events'
                """, Integer.class);

        String outboxPayloadDataType = jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'outbox_events'
                  AND column_name = 'payload'
                """, String.class);

        String outboxStatusDefault = jdbcTemplate.queryForObject("""
                SELECT column_default
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'outbox_events'
                  AND column_name = 'status'
                """, String.class);

        Integer pendingOutboxIndexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'outbox_events'
                  AND indexname = 'idx_outbox_events_pending'
                """, Integer.class);

        String currencyDataType = jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'order_proposals'
                  AND column_name = 'currency'
                """, String.class);

        String statusDefault = jdbcTemplate.queryForObject("""
                SELECT column_default
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'order_proposals'
                  AND column_name = 'status'
                """, String.class);

        Integer expectedConstraintCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = 'public'
                  AND constraint_name IN (
                    'chk_order_proposals_status',
                    'chk_order_proposals_rationale',
                    'chk_order_proposal_evidence_sources_position'
                  )
                """, Integer.class);

        Integer sourceColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'order_proposals'
                  AND column_name = 'source'
                """, Integer.class);

        assertThat(tableCount).isEqualTo(2);
        assertThat(changeSetCount).isEqualTo(2);
        assertThat(outboxTableCount).isEqualTo(1);
        assertThat(outboxPayloadDataType).isEqualTo("jsonb");
        assertThat(outboxStatusDefault).contains("PENDING");
        assertThat(pendingOutboxIndexCount).isEqualTo(1);
        assertThat(currencyDataType).isEqualTo("character varying");
        assertThat(statusDefault).contains("PROPOSED");
        assertThat(expectedConstraintCount).isEqualTo(3);
        assertThat(sourceColumnCount).isEqualTo(1);
    }
}
