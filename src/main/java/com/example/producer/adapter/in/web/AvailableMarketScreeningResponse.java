package com.example.producer.adapter.in.web;

import com.example.producer.application.port.in.AvailableMarketScreeningResult;

import java.time.LocalDate;
import java.util.List;

public record AvailableMarketScreeningResponse(
        String snapshotId,
        LocalDate asOfSession,
        boolean synthetic,
        boolean researchOnly,
        List<InstrumentSignal> instruments
) {
    static AvailableMarketScreeningResponse from(AvailableMarketScreeningResult result) {
        return new AvailableMarketScreeningResponse(
                result.snapshotId(), result.asOfSession(), result.synthetic(), true,
                result.instruments().stream()
                        .map(instrument -> new InstrumentSignal(
                                instrument.market(), instrument.symbol(), instrument.currency(),
                                MarketScreeningResponse.SignalResponse.from(instrument.signal())))
                        .toList()
        );
    }

    public record InstrumentSignal(
            String market,
            String symbol,
            String currency,
            MarketScreeningResponse.SignalResponse signal
    ) {
    }
}
