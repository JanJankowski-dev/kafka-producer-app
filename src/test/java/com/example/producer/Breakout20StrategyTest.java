package com.example.producer;

import com.example.producer.application.port.in.ScreenMarketCommand;
import com.example.producer.application.service.MarketScreeningService;
import com.example.producer.domain.strategy.AssetType;
import com.example.producer.domain.strategy.Breakout20Strategy;
import com.example.producer.domain.strategy.DailyClose;
import com.example.producer.domain.strategy.InstrumentHistory;
import com.example.producer.domain.strategy.OpenPosition;
import com.example.producer.domain.strategy.SignalAction;
import com.example.producer.domain.strategy.TradingSignal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Breakout20StrategyTest {
    private static final LocalDate FIRST_SESSION = LocalDate.of(2026, 9, 1);
    private static final LocalDate AS_OF = FIRST_SESSION.plusDays(20);
    private final Breakout20Strategy strategy = new Breakout20Strategy();

    @Test
    void screensStocksAndEtfsAndRanksStrongerBreakoutFirst() {
        var stock = history(UUID.randomUUID(), AssetType.STOCK, increasingCloses(1), null);
        var etf = history(UUID.randomUUID(), AssetType.ETF, increasingCloses(2), null);

        var result = new MarketScreeningService(strategy)
                .screen(new ScreenMarketCommand(AS_OF, List.of(stock, etf)));

        assertThat(result.signals()).extracting(TradingSignal::instrumentId)
                .containsExactly(etf.instrumentId(), stock.instrumentId());
        assertThat(result.signals()).extracting(TradingSignal::action)
                .containsExactly(SignalAction.BUY, SignalAction.BUY);
        assertThat(result.signals().get(0).returnOver20SessionsPct())
                .isEqualByComparingTo("40.0000");
        assertThat(result.signals().get(0).maxHoldingSessions()).isEqualTo(20);
    }

    @Test
    void doesNotBuyWithoutARealBreakoutOrSufficientHistory() {
        var noBreakout = new ArrayList<>(increasingCloses(1));
        noBreakout.set(20, new DailyClose(AS_OF, new BigDecimal("115")));

        var result = strategy.evaluate(
                history(UUID.randomUUID(), AssetType.STOCK, noBreakout, null), AS_OF);
        assertThat(result.action()).isEqualTo(SignalAction.NO_ACTION);
        assertThat(result.reasonCode()).isEqualTo("NO_BREAKOUT");

        var shortHistory = strategy.evaluate(history(UUID.randomUUID(), AssetType.ETF,
                increasingCloses(1).subList(0, 20), null), FIRST_SESSION.plusDays(19));
        assertThat(shortHistory.action()).isEqualTo(SignalAction.NO_ACTION);
        assertThat(shortHistory.reasonCode()).isEqualTo("INSUFFICIENT_ENTRY_HISTORY");
    }

    @Test
    void exitsAfterTwentyHeldSessionsOrWhenCloseFallsBelowTenSessionAverage() {
        var atTimeLimit = strategy.evaluate(history(UUID.randomUUID(), AssetType.STOCK,
                increasingCloses(1), new OpenPosition(20)), AS_OF);
        assertThat(atTimeLimit.action()).isEqualTo(SignalAction.SELL);
        assertThat(atTimeLimit.reasonCode()).isEqualTo("MAX_HOLDING_PERIOD");

        var falling = new ArrayList<>(increasingCloses(1));
        falling.set(20, new DailyClose(AS_OF, new BigDecimal("90")));
        var belowAverage = strategy.evaluate(history(UUID.randomUUID(), AssetType.ETF,
                falling, new OpenPosition(5)), AS_OF);
        assertThat(belowAverage.action()).isEqualTo(SignalAction.SELL);
        assertThat(belowAverage.reasonCode()).isEqualTo("BELOW_10_SESSION_AVERAGE");
    }

    @Test
    void neverUsesABarFromAfterTheEvaluationSession() {
        var instrument = history(UUID.randomUUID(), AssetType.STOCK,
                increasingCloses(1), null);

        assertThatThrownBy(() -> strategy.evaluate(instrument, AS_OF.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Market history cannot contain future sessions");
    }

    @Test
    void rejectsUnsortedOrDuplicateSessionsAndDuplicateInstruments() {
        var duplicateDates = List.of(
                new DailyClose(FIRST_SESSION, BigDecimal.ONE),
                new DailyClose(FIRST_SESSION, BigDecimal.TEN)
        );
        assertThatThrownBy(() -> history(UUID.randomUUID(), AssetType.STOCK, duplicateDates, null))
                .isInstanceOf(IllegalArgumentException.class);

        var instrument = history(UUID.randomUUID(), AssetType.STOCK, increasingCloses(1), null);
        assertThatThrownBy(() -> new MarketScreeningService(strategy)
                .screen(new ScreenMarketCommand(AS_OF, List.of(instrument, instrument))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Each instrumentId must occur only once");
    }

    private InstrumentHistory history(
            UUID instrumentId,
            AssetType type,
            List<DailyClose> closes,
            OpenPosition position
    ) {
        return new InstrumentHistory(instrumentId, type, closes, position);
    }

    private List<DailyClose> increasingCloses(int dailyIncrease) {
        var closes = new ArrayList<DailyClose>();
        for (int index = 0; index <= 20; index++) {
            closes.add(new DailyClose(FIRST_SESSION.plusDays(index),
                    BigDecimal.valueOf(100L + (long) index * dailyIncrease)));
        }
        return closes;
    }
}
