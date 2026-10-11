package com.example.producer.application.service;

import com.example.producer.application.port.out.MarketImportStorePort;
import com.example.producer.domain.strategy.AssetType;
import com.example.producer.domain.strategy.Breakout20Strategy;
import com.example.producer.domain.strategy.DailyClose;
import com.example.producer.domain.strategy.InstrumentHistory;
import com.example.producer.domain.strategy.SignalAction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class MarketSignalHistoryService {
    private static final int LOOKBACK = 20;
    private static final int FORWARD_SESSIONS = 5;

    private final MarketImportStorePort imports;
    private final Breakout20Strategy strategy = new Breakout20Strategy();

    public MarketSignalHistoryService(MarketImportStorePort imports) {
        this.imports = imports;
    }

    public SignalHistory history() {
        var batch = imports.latestHistories();
        var observations = new ArrayList<BuyObservation>();
        for (var history : batch.histories()) {
            var prices = history.prices();
            for (int index = LOOKBACK; index < prices.size(); index++) {
                var closes = prices.subList(index - LOOKBACK, index + 1).stream()
                        .map(price -> new DailyClose(price.sessionDate(), price.adjustedClose()))
                        .toList();
                var instrument = new InstrumentHistory(history.instrument().instrumentId(),
                        AssetType.STOCK, closes, null);
                var signal = strategy.evaluate(instrument, prices.get(index).sessionDate());
                if (signal.action() != SignalAction.BUY) continue;
                BigDecimal forwardReturn = index + FORWARD_SESSIONS < prices.size()
                        ? prices.get(index + FORWARD_SESSIONS).adjustedClose()
                                .subtract(prices.get(index).adjustedClose())
                                .multiply(BigDecimal.valueOf(100))
                                .divide(prices.get(index).adjustedClose(), 4, RoundingMode.HALF_UP)
                        : null;
                observations.add(new BuyObservation(history.instrument().symbol(),
                        prices.get(index).sessionDate(), prices.get(index).adjustedClose(),
                        forwardReturn));
            }
        }
        observations.sort(Comparator.comparing(BuyObservation::session).reversed()
                .thenComparing(BuyObservation::symbol));
        return new SignalHistory(batch.status().batchId(), batch.status().latestCommonSession(),
                batch.status().sessionCount(), batch.histories().size(),
                "BREAKOUT_20_V1", FORWARD_SESSIONS, true, List.copyOf(observations));
    }

    public record SignalHistory(UUID batchId, LocalDate latestCommonSession, int sessionCount,
                                int instrumentCount, String strategyVersion,
                                int forwardSessions, boolean researchOnly,
                                List<BuyObservation> buyObservations) {
    }

    public record BuyObservation(String symbol, LocalDate session,
                                 BigDecimal adjustedCloseAtSignal,
                                 BigDecimal forwardAdjustedCloseReturnPct) {
    }
}
