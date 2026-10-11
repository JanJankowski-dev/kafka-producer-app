package com.example.producer.adapter.out.persistence;

import com.example.producer.application.port.out.StrategyComparisonStorePort;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static com.example.producer.jooq.tables.MarketImportBatches.MARKET_IMPORT_BATCHES;
import static com.example.producer.jooq.tables.PaperCash.PAPER_CASH;
import static com.example.producer.jooq.tables.PaperPortfolios.PAPER_PORTFOLIOS;
import static com.example.producer.jooq.tables.StrategyComparisons.STRATEGY_COMPARISONS;

@Repository
public class JooqStrategyComparisonStore implements StrategyComparisonStorePort {
    private static final BigDecimal STARTING_USD = new BigDecimal("10000.00000000");
    private final DSLContext dsl;

    public JooqStrategyComparisonStore(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public ComparisonView createOrFind(UUID batchId, String strategyVersion) {
        var batch = dsl.selectFrom(MARKET_IMPORT_BATCHES)
                .where(MARKET_IMPORT_BATCHES.BATCH_ID.eq(batchId))
                .forUpdate().fetchOne();
        if (batch == null) throw new IllegalArgumentException("Imported market batch not found");
        var existing = findByBatch(batchId);
        if (existing != null) return existing;

        UUID traditionalId = createUsdPortfolio();
        UUID aiId = createUsdPortfolio();
        UUID comparisonId = UUID.randomUUID();
        dsl.insertInto(STRATEGY_COMPARISONS)
                .set(STRATEGY_COMPARISONS.COMPARISON_ID, comparisonId)
                .set(STRATEGY_COMPARISONS.MARKET_IMPORT_BATCH_ID, batchId)
                .set(STRATEGY_COMPARISONS.TRADITIONAL_PORTFOLIO_ID, traditionalId)
                .set(STRATEGY_COMPARISONS.AI_PORTFOLIO_ID, aiId)
                .set(STRATEGY_COMPARISONS.TRADITIONAL_STRATEGY_VERSION, strategyVersion)
                .execute();
        return findByBatch(batchId);
    }

    @Override
    @Transactional(readOnly = true)
    public ComparisonView findByBatch(UUID batchId) {
        var record = dsl.selectFrom(STRATEGY_COMPARISONS)
                .where(STRATEGY_COMPARISONS.MARKET_IMPORT_BATCH_ID.eq(batchId))
                .fetchOne();
        return toView(record);
    }

    @Override
    @Transactional(readOnly = true)
    public ComparisonView latest() {
        var record = dsl.selectFrom(STRATEGY_COMPARISONS)
                .orderBy(STRATEGY_COMPARISONS.CREATED_AT.desc(),
                        STRATEGY_COMPARISONS.COMPARISON_ID.desc())
                .limit(1).fetchOne();
        return toView(record);
    }

    private ComparisonView toView(com.example.producer.jooq.tables.records.StrategyComparisonsRecord record) {
        return record == null ? null : new ComparisonView(record.getComparisonId(),
                record.getMarketImportBatchId(), record.getTraditionalPortfolioId(),
                record.getAiPortfolioId(), record.getTraditionalStrategyVersion(),
                record.getAiModelVersion(), record.getCreatedAt());
    }

    private UUID createUsdPortfolio() {
        UUID id = UUID.randomUUID();
        dsl.insertInto(PAPER_PORTFOLIOS).set(PAPER_PORTFOLIOS.PORTFOLIO_ID, id).execute();
        dsl.insertInto(PAPER_CASH)
                .set(PAPER_CASH.PORTFOLIO_ID, id)
                .set(PAPER_CASH.CURRENCY, "USD")
                .set(PAPER_CASH.BALANCE, STARTING_USD)
                .execute();
        return id;
    }
}
