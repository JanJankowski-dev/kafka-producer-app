package com.example.producer;

import com.example.producer.application.port.out.PaperPortfolioStorePort.TradeCommand;
import com.example.producer.application.service.MarketResearchService;
import com.example.producer.application.service.PaperPortfolioService;
import com.example.producer.configuration.PostgresIntegrationTest;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.example.producer.jooq.tables.MarketDailyCloses.MARKET_DAILY_CLOSES;
import static com.example.producer.jooq.tables.ScreeningSignals.SCREENING_SIGNALS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
class MarketResearchAndPaperPortfolioIntegrationTest {
    @Autowired private MarketResearchService research;
    @Autowired private PaperPortfolioService paper;
    @Autowired private DSLContext dsl;

    @Test
    void savesImmutableDailyClosesAndIdempotentScreeningRun() {
        var first = research.run();
        var second = research.run();

        assertThat(second.runId()).isEqualTo(first.runId());
        assertThat(dsl.fetchCount(MARKET_DAILY_CLOSES)).isEqualTo(84);
        assertThat(dsl.fetchCount(SCREENING_SIGNALS)).isEqualTo(4);
        assertThat(first.screening().synthetic()).isTrue();
    }

    @Test
    void buysAndSellsOnlyInsideTheSelectedPaperPortfolioAndReplaysSameOrderOnce() {
        var run = research.run();
        var instrument = run.screening().instruments().stream()
                .filter(item -> item.symbol().equals("US-ETF-DEMO"))
                .findFirst().orElseThrow();
        UUID portfolioId = paper.create();
        UUID otherPortfolioId = paper.create();
        var buy = new TradeCommand(UUID.randomUUID(), run.runId(),
                instrument.signal().instrumentId(), "BUY", new BigDecimal("2"));

        var first = paper.trade(portfolioId, buy);
        var replay = paper.trade(portfolioId, buy);
        assertThat(replay.tradeId()).isEqualTo(first.tradeId());
        assertThat(first.price()).isEqualByComparingTo("140");
        assertThat(paper.view(portfolioId).cash().stream()
                .filter(cash -> cash.currency().equals("USD")).findFirst().orElseThrow().balance())
                .isEqualByComparingTo("9720");
        assertThat(paper.view(portfolioId).positions()).hasSize(1);
        assertThat(paper.view(portfolioId).trades()).hasSize(1);
        assertThat(paper.view(otherPortfolioId).positions()).isEmpty();

        var sell = new TradeCommand(UUID.randomUUID(), run.runId(),
                instrument.signal().instrumentId(), "SELL", new BigDecimal("1"));
        paper.trade(portfolioId, sell);
        assertThat(paper.view(portfolioId).positions().get(0).quantity()).isEqualByComparingTo("1");
        assertThat(paper.view(portfolioId).cash().stream()
                .filter(cash -> cash.currency().equals("USD")).findFirst().orElseThrow().balance())
                .isEqualByComparingTo("9860");
    }

    @Test
    void rejectsOverspendingShortSellingAndChangedReplayWithoutChangingState() {
        var run = research.run();
        UUID instrumentId = run.screening().instruments().stream()
                .filter(item -> item.symbol().equals("US-ETF-DEMO"))
                .findFirst().orElseThrow().signal().instrumentId();
        UUID portfolioId = paper.create();

        assertThatThrownBy(() -> paper.trade(portfolioId,
                new TradeCommand(UUID.randomUUID(), run.runId(), instrumentId,
                        "SELL", BigDecimal.ONE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paper.trade(portfolioId,
                new TradeCommand(UUID.randomUUID(), run.runId(), instrumentId,
                        "BUY", new BigDecimal("1000"))))
                .isInstanceOf(IllegalArgumentException.class);

        UUID orderId = UUID.randomUUID();
        paper.trade(portfolioId, new TradeCommand(orderId, run.runId(), instrumentId,
                "BUY", BigDecimal.ONE));
        assertThatThrownBy(() -> paper.trade(portfolioId,
                new TradeCommand(orderId, run.runId(), instrumentId,
                        "BUY", new BigDecimal("2"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(paper.view(portfolioId).trades()).hasSize(1);
    }

    @Test
    void concurrentBuysCannotSpendTheSameCashTwice() throws Exception {
        var run = research.run();
        UUID instrumentId = run.screening().instruments().stream()
                .filter(item -> item.symbol().equals("US-ETF-DEMO"))
                .findFirst().orElseThrow().signal().instrumentId();
        UUID portfolioId = paper.create();
        var start = new CountDownLatch(1);
        Callable<Boolean> buy = () -> {
            start.await();
            try {
                paper.trade(portfolioId, new TradeCommand(UUID.randomUUID(), run.runId(),
                        instrumentId, "BUY", new BigDecimal("50")));
                return true;
            } catch (IllegalArgumentException expected) {
                return false;
            }
        };
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(buy);
            var second = pool.submit(buy);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
        assertThat(paper.view(portfolioId).trades()).hasSize(1);
        assertThat(paper.view(portfolioId).cash().stream()
                .filter(cash -> cash.currency().equals("USD")).findFirst().orElseThrow().balance())
                .isEqualByComparingTo("3000");
    }
}
