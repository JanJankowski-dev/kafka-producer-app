package com.example.producer;

import com.example.producer.application.port.out.AiDecisionPort;
import com.example.producer.application.port.out.HistoricalPricesPort.DailyPrice;
import com.example.producer.application.port.out.MarketImportStorePort;
import com.example.producer.application.port.out.PaperPortfolioStorePort;
import com.example.producer.application.service.AutoPaperTradingService;
import com.example.producer.application.service.PaperDailyValuationService;
import com.example.producer.application.service.StrategyComparisonService;
import com.example.producer.configuration.PostgresIntegrationTest;
import com.example.producer.domain.market.Sp500Top10Universe;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.example.producer.jooq.tables.PaperAutoDecisions.PAPER_AUTO_DECISIONS;
import static com.example.producer.jooq.tables.PaperAutoOrders.PAPER_AUTO_ORDERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

@PostgresIntegrationTest
class AutoPaperTradingIntegrationTest {
    @Autowired private MarketImportStorePort imports;
    @Autowired private StrategyComparisonService comparisons;
    @Autowired private AutoPaperTradingService trading;
    @Autowired private PaperDailyValuationService valuations;
    @Autowired private DSLContext dsl;
    @Autowired private PaperPortfolioStorePort portfolios;
    @MockitoBean private AiDecisionPort ai;

    @Test
    void decidesAfterCloseFillsOnlyAtNextOpenAndIsIdempotent() {
        when(ai.configured()).thenReturn(true);
        when(ai.modelVersion()).thenReturn("TEST_MODEL");
        when(ai.decide(any(), any())).thenAnswer(invocation -> {
            List<AiDecisionPort.Candidate> candidates = invocation.getArgument(1);
            return candidates.stream().map(candidate -> new AiDecisionPort.Decision(
                    candidate.symbol(), candidate.symbol().equals("NVDA") ? "BUY" : "HOLD",
                    "TEST_ONLY")).toList();
        });
        LocalDate first = LocalDate.of(2026, 9, 1);
        var dayOne = save(first, 21);
        var pair = comparisons.createForLatestBatch();

        var initial = trading.runLatest();
        assertThat(initial.traditionalDecisions()).isEqualTo(10);
        assertThat(initial.aiDecisions()).isEqualTo(10);
        assertThat(dsl.fetchCount(PAPER_AUTO_ORDERS,
                PAPER_AUTO_ORDERS.PORTFOLIO_ID.eq(pair.aiPortfolioId())
                        .and(PAPER_AUTO_ORDERS.STATUS.eq("PENDING")))).isEqualTo(1);
        assertThat(dsl.fetchCount(PAPER_AUTO_ORDERS,
                PAPER_AUTO_ORDERS.STATUS.eq("FILLED"))).isZero();
        assertThat(trading.runLatest().traditionalDecisions()).isZero();
        assertThat(dsl.fetchCount(PAPER_AUTO_DECISIONS)).isEqualTo(20);
        assertThat(valuations.recordLatest(dayOne.batchId(), dayOne.latestCommonSession()))
                .allSatisfy(mark -> assertThat(mark.equityUsd()).isEqualByComparingTo("10000"));

        var dayTwo = save(first, 22);
        trading.runLatest();
        assertThat(dsl.fetchCount(PAPER_AUTO_ORDERS,
                PAPER_AUTO_ORDERS.PORTFOLIO_ID.eq(pair.aiPortfolioId())
                        .and(PAPER_AUTO_ORDERS.STATUS.eq("FILLED")))).isEqualTo(1);
        assertThat(dsl.fetchCount(PAPER_AUTO_ORDERS,
                PAPER_AUTO_ORDERS.PORTFOLIO_ID.eq(pair.traditionalPortfolioId())
                        .and(PAPER_AUTO_ORDERS.STATUS.eq("FILLED")))).isEqualTo(3);
        var marks = valuations.recordLatest(dayTwo.batchId(), dayTwo.latestCommonSession());
        assertThat(marks).hasSize(2).allSatisfy(mark -> {
            assertThat(mark.positionsUsd()).isPositive();
            assertThat(mark.equityUsd()).isEqualByComparingTo(
                    mark.cashUsd().add(mark.positionsUsd()));
        });
        assertThat(trading.runLatest().aiDecisions()).isZero();
        assertThat(dsl.fetchCount(PAPER_AUTO_DECISIONS)).isEqualTo(40);

        var beforeDividend = portfolios.view(pair.aiPortfolioId());
        BigDecimal beforeCash = beforeDividend.cash().get(0).balance();
        BigDecimal heldQuantity = beforeDividend.positions().get(0).quantity();
        doReturn(List.of(new AiDecisionPort.Decision("INVALID", "BUY", "INVALID")))
                .when(ai).decide(any(), any());
        save(first, 23);
        trading.runLatest();
        var afterDividend = portfolios.view(pair.aiPortfolioId());
        assertThat(afterDividend.cash().get(0).balance()).isEqualByComparingTo(
                beforeCash.add(heldQuantity.multiply(new BigDecimal("0.50"))
                        .setScale(8, java.math.RoundingMode.HALF_UP)));
        assertThat(dsl.fetchCount(PAPER_AUTO_DECISIONS,
                PAPER_AUTO_DECISIONS.PORTFOLIO_ID.eq(pair.aiPortfolioId())
                        .and(PAPER_AUTO_DECISIONS.SESSION_DATE.eq(first.plusDays(22)))
                        .and(PAPER_AUTO_DECISIONS.ACTION.eq("HOLD")))).isEqualTo(10);
        assertThat(trading.runLatest().aiDecisions()).isZero();
    }

    private MarketImportStorePort.ImportStatus save(LocalDate first, int days) {
        var histories = Sp500Top10Universe.instruments().stream()
                .map(instrument -> new MarketImportStorePort.ImportedHistory(instrument,
                        IntStream.range(0, days).mapToObj(day -> new DailyPrice(first.plusDays(day),
                                BigDecimal.valueOf(99 + day), BigDecimal.valueOf(100 + day),
                                BigDecimal.valueOf(100 + day), 1000, BigDecimal.ONE,
                                day == 22 && instrument.symbol().equals("NVDA")
                                        ? new BigDecimal("0.50") : BigDecimal.ZERO))
                                .toList()))
                .toList();
        return imports.save(UUID.randomUUID().toString().replace("-", "") + "0".repeat(32),
                first.plusDays(days - 1), days, histories);
    }
}
