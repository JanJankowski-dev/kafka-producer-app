package com.example.producer.application.port.in;

import com.example.producer.domain.strategy.TradingSignal;

import java.time.LocalDate;
import java.util.List;

public record AvailableMarketScreeningResult(
        String snapshotId,
        LocalDate asOfSession,
        boolean synthetic,
        List<ScreenedInstrument> instruments
) {
    public AvailableMarketScreeningResult {
        instruments = List.copyOf(instruments);
    }

    public record ScreenedInstrument(
            String market,
            String symbol,
            String currency,
            TradingSignal signal
    ) {
    }
}
