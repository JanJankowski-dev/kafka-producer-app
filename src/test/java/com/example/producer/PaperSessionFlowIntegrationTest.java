package com.example.producer;

import com.example.producer.application.service.PaperPortfolioService;
import com.example.producer.application.service.PaperSimulationService;
import com.example.producer.configuration.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
class PaperSessionFlowIntegrationTest {
    private static final UUID US_ETF = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Autowired private PaperSimulationService simulation;
    @Autowired private PaperPortfolioService portfolios;

    @Test
    void orderWaitsForNextSessionThenPortfolioPositionChangesTheSignal() {
        UUID portfolioId = portfolios.create();
        var dates = simulation.availableSessions();
        var first = simulation.advance(portfolioId, dates.get(44));
        assertThat(action(first)).isEqualTo("BUY");

        UUID clientOrderId = UUID.randomUUID();
        var order = simulation.submit(portfolioId, clientOrderId, first.runId(),
                US_ETF, "BUY", BigDecimal.ONE);
        assertThat(order.status()).isEqualTo("PENDING");
        assertThat(simulation.submit(portfolioId, clientOrderId, first.runId(),
                US_ETF, "BUY", BigDecimal.ONE).orderId()).isEqualTo(order.orderId());
        assertThat(portfolios.view(portfolioId).positions()).isEmpty();
        assertThat(portfolios.view(portfolioId).cash().stream()
                .filter(cash -> cash.currency().equals("USD")).findFirst().orElseThrow().balance())
                .isEqualByComparingTo("10000");
        assertThat(simulation.advance(portfolioId, dates.get(44)).runId()).isEqualTo(first.runId());

        var second = simulation.advance(portfolioId, dates.get(45));
        assertThat(second.settledOrders()).hasSize(1);
        assertThat(second.settledOrders().get(0).status()).isEqualTo("FILLED");
        assertThat(portfolios.view(portfolioId).trades().get(0).price()).isEqualByComparingTo("190");
        assertThat(portfolios.view(portfolioId).cash().stream()
                .filter(cash -> cash.currency().equals("USD")).findFirst().orElseThrow().balance())
                .isEqualByComparingTo("9810");
        assertThat(action(second)).isEqualTo("HOLD");

        UUID anotherPortfolio = portfolios.create();
        assertThat(action(simulation.advance(anotherPortfolio, dates.get(45)))).isEqualTo("BUY");

        var third = simulation.advance(portfolioId, dates.get(46));
        assertThat(action(third)).isEqualTo("SELL");
        simulation.submit(portfolioId, UUID.randomUUID(), third.runId(), US_ETF,
                "SELL", BigDecimal.ONE);
        assertThat(portfolios.view(portfolioId).positions()).hasSize(1);
        var fourth = simulation.advance(portfolioId, dates.get(47));
        assertThat(fourth.settledOrders().get(0).status()).isEqualTo("FILLED");
        assertThat(portfolios.view(portfolioId).positions()).isEmpty();
    }

    @Test
    void rejectsOversizedFillWithoutRollingBackTheNextSession() {
        UUID portfolioId = portfolios.create();
        var dates = simulation.availableSessions();
        var first = simulation.advance(portfolioId, dates.get(44));
        simulation.submit(portfolioId, UUID.randomUUID(), first.runId(), US_ETF,
                "BUY", new BigDecimal("1000"));

        assertThatThrownBy(() -> simulation.advance(portfolioId, dates.get(46)))
                .isInstanceOf(IllegalArgumentException.class);
        var second = simulation.advance(portfolioId, dates.get(45));
        assertThat(second.settledOrders().get(0).status()).isEqualTo("REJECTED");
        assertThat(portfolios.view(portfolioId).positions()).isEmpty();
        assertThat(portfolios.view(portfolioId).lastSession()).isEqualTo(dates.get(45));
    }

    @Test
    void onlyCurrentPortfolioSignalCanCreateAnOrder() {
        UUID portfolioId = portfolios.create();
        var dates = simulation.availableSessions();
        var first = simulation.advance(portfolioId, dates.get(44));
        assertThatThrownBy(() -> simulation.submit(portfolioId, UUID.randomUUID(), first.runId(),
                US_ETF, "SELL", BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        UUID another = portfolios.create();
        simulation.advance(another, dates.get(44));
        assertThatThrownBy(() -> simulation.submit(another, UUID.randomUUID(), first.runId(),
                US_ETF, "BUY", BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
    }

    private String action(PaperSimulationService.SessionResult result) {
        return result.screening().instruments().stream()
                .filter(item -> item.signal().instrumentId().equals(US_ETF))
                .findFirst().orElseThrow().signal().action().name();
    }
}
