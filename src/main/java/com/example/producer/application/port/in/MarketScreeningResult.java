package com.example.producer.application.port.in;

import com.example.producer.domain.strategy.TradingSignal;

import java.util.List;

public record MarketScreeningResult(List<TradingSignal> signals) {
    public MarketScreeningResult {
        signals = List.copyOf(signals);
    }
}
