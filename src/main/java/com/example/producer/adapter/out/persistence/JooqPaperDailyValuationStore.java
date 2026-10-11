package com.example.producer.adapter.out.persistence;

import com.example.producer.application.port.out.PaperDailyValuationStorePort;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.example.producer.jooq.tables.PaperDailyValuations.PAPER_DAILY_VALUATIONS;

@Repository
public class JooqPaperDailyValuationStore implements PaperDailyValuationStorePort {
    private final DSLContext dsl;

    public JooqPaperDailyValuationStore(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public List<Valuation> savePair(UUID comparisonId, UUID batchId, LocalDate session,
                                    UUID traditionalPortfolioId, Mark traditional,
                                    UUID aiPortfolioId, Mark ai) {
        saveOne(comparisonId, batchId, session, traditionalPortfolioId, traditional);
        saveOne(comparisonId, batchId, session, aiPortfolioId, ai);
        return dsl.selectFrom(PAPER_DAILY_VALUATIONS)
                .where(PAPER_DAILY_VALUATIONS.COMPARISON_ID.eq(comparisonId))
                .and(PAPER_DAILY_VALUATIONS.MARKET_IMPORT_BATCH_ID.eq(batchId))
                .orderBy(PAPER_DAILY_VALUATIONS.PORTFOLIO_ID)
                .fetch(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Valuation> history(UUID comparisonId) {
        return dsl.selectFrom(PAPER_DAILY_VALUATIONS)
                .where(PAPER_DAILY_VALUATIONS.COMPARISON_ID.eq(comparisonId))
                .orderBy(PAPER_DAILY_VALUATIONS.SESSION_DATE.desc(),
                        PAPER_DAILY_VALUATIONS.RECORDED_AT.desc())
                .limit(120)
                .fetch(this::toView);
    }

    private void saveOne(UUID comparisonId, UUID batchId, LocalDate session,
                         UUID portfolioId, Mark mark) {
        BigDecimal cashUsd = mark.cashUsd();
        BigDecimal positionsUsd = mark.positionsUsd();
        int inserted = dsl.insertInto(PAPER_DAILY_VALUATIONS)
                .set(PAPER_DAILY_VALUATIONS.COMPARISON_ID, comparisonId)
                .set(PAPER_DAILY_VALUATIONS.PORTFOLIO_ID, portfolioId)
                .set(PAPER_DAILY_VALUATIONS.MARKET_IMPORT_BATCH_ID, batchId)
                .set(PAPER_DAILY_VALUATIONS.SESSION_DATE, session)
                .set(PAPER_DAILY_VALUATIONS.CASH_USD, cashUsd)
                .set(PAPER_DAILY_VALUATIONS.POSITIONS_USD, positionsUsd)
                .set(PAPER_DAILY_VALUATIONS.EQUITY_USD, cashUsd.add(positionsUsd))
                .onConflictDoNothing().execute();
        if (inserted == 0) {
            var existing = dsl.selectFrom(PAPER_DAILY_VALUATIONS)
                    .where(PAPER_DAILY_VALUATIONS.COMPARISON_ID.eq(comparisonId))
                    .and(PAPER_DAILY_VALUATIONS.PORTFOLIO_ID.eq(portfolioId))
                    .and(PAPER_DAILY_VALUATIONS.MARKET_IMPORT_BATCH_ID.eq(batchId))
                    .fetchOne();
            if (existing == null || !existing.getSessionDate().equals(session)
                    || existing.getCashUsd().compareTo(cashUsd) != 0
                    || existing.getPositionsUsd().compareTo(positionsUsd) != 0) {
                throw new IllegalStateException("Existing paper valuation differs from current state");
            }
        }
    }

    private Valuation toView(com.example.producer.jooq.tables.records.PaperDailyValuationsRecord row) {
        return new Valuation(row.getComparisonId(), row.getPortfolioId(),
                row.getMarketImportBatchId(), row.getSessionDate(), row.getCashUsd(),
                row.getPositionsUsd(), row.getEquityUsd(), row.getRecordedAt());
    }
}
