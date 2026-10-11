package com.example.producer.application.port.out;

import com.example.producer.domain.strategy.InstrumentHistory;

import java.util.Objects;

public record MarketInstrument(
        String market,
        String symbol,
        String currency,
        InstrumentHistory history
) {
    public MarketInstrument {
        Objects.requireNonNull(market, "market must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(history, "history must not be null");
    }
}
