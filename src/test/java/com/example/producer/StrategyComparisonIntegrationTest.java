package com.example.producer;

import com.example.producer.application.port.out.HistoricalPricesPort.DailyPrice;
import com.example.producer.application.port.out.MarketImportStorePort;
import com.example.producer.application.port.out.PaperPortfolioStorePort;
import com.example.producer.application.service.StrategyComparisonService;
import com.example.producer.application.service.PaperDailyValuationService;
import com.example.producer.configuration.PostgresIntegrationTest;
import com.example.producer.domain.market.Sp500Top10Universe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
class StrategyComparisonIntegrationTest {
    @Autowired private MarketImportStorePort imports;
    @Autowired private StrategyComparisonService comparisons;
    @Autowired private PaperPortfolioStorePort portfolios;
    @Autowired private PaperDailyValuationService valuations;

    @Test
    void createsTwoDistinctEqualStartingPortfoliosAndPinsOneImport() {
        LocalDate first = LocalDate.of(2026, 8, 1);
        var histories = Sp500Top10Universe.instruments().stream()
                .map(instrument -> new MarketImportStorePort.ImportedHistory(instrument,
                        IntStream.range(0, 21).mapToObj(day -> new DailyPrice(first.plusDays(day),
                                BigDecimal.valueOf(100 + day), BigDecimal.valueOf(100 + day), 1000))
                                .toList()))
                .toList();
        var batch = imports.save(UUID.randomUUID().toString().replace("-", "") + "0".repeat(32),
                first.plusDays(20), 21, histories);

        var firstComparison = comparisons.createForLatestBatch();
        var replay = comparisons.createForLatestBatch();

        assertThat(replay.comparisonId()).isEqualTo(firstComparison.comparisonId());
        assertThat(firstComparison.marketImportBatchId()).isEqualTo(batch.batchId());
        assertThat(firstComparison.traditionalPortfolioId())
                .isNotEqualTo(firstComparison.aiPortfolioId());
        assertThat(firstComparison.traditionalStrategyVersion()).isEqualTo("BREAKOUT_20_V1");
        assertThat(firstComparison.aiModelVersion()).isNull();
        for (UUID portfolioId : List.of(firstComparison.traditionalPortfolioId(),
                firstComparison.aiPortfolioId())) {
            var view = portfolios.view(portfolioId);
            assertThat(view.cash()).hasSize(1);
            assertThat(view.cash().get(0).currency()).isEqualTo("USD");
            assertThat(view.cash().get(0).balance()).isEqualByComparingTo("10000");
            assertThat(view.trades()).isEmpty();
        }

        assertThat(valuations.recordLatest(batch.batchId(), batch.latestCommonSession()))
                .hasSize(2);
        assertThat(valuations.recordLatest(batch.batchId(), batch.latestCommonSession()))
                .hasSize(2);
        assertThat(valuations.history()).hasSize(2).allSatisfy(valuation -> {
            assertThat(valuation.cashUsd()).isEqualByComparingTo("10000");
            assertThat(valuation.positionsUsd()).isEqualByComparingTo("0");
            assertThat(valuation.equityUsd()).isEqualByComparingTo("10000");
        });

        var laterBatch = imports.save(UUID.randomUUID().toString().replace("-", "") + "1".repeat(32),
                first.plusDays(20), 21, histories);
        assertThat(laterBatch.batchId()).isNotEqualTo(batch.batchId());
        assertThat(comparisons.latest().comparisonId()).isEqualTo(firstComparison.comparisonId());
    }
}
