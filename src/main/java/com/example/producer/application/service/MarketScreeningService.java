package com.example.producer.application.service;

import com.example.producer.application.port.in.MarketScreeningResult;
import com.example.producer.application.port.in.ScreenMarketCommand;
import com.example.producer.application.port.in.ScreenMarketUseCase;
import com.example.producer.domain.strategy.Breakout20Strategy;
import com.example.producer.domain.strategy.SignalAction;
import com.example.producer.domain.strategy.TradingSignal;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;

public class MarketScreeningService implements ScreenMarketUseCase {
    private final Breakout20Strategy strategy;

    public MarketScreeningService(Breakout20Strategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public MarketScreeningResult screen(ScreenMarketCommand command) {
        var seen = new HashSet<java.util.UUID>();
        for (var instrument : command.instruments()) {
            if (!seen.add(instrument.instrumentId())) {
                throw new IllegalArgumentException("Each instrumentId must occur only once");
            }
        }

        var signals = command.instruments().stream()
                .map(instrument -> strategy.evaluate(instrument, command.asOfSession()))
                .sorted(Comparator.comparingInt((TradingSignal signal) -> priority(signal.action()))
                        .thenComparing(TradingSignal::returnOver20SessionsPct,
                                Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder()))
                        .thenComparing(TradingSignal::instrumentId))
                .toList();
        return new MarketScreeningResult(signals);
    }

    private int priority(SignalAction action) {
        return switch (action) {
            case SELL -> 0;
            case BUY -> 1;
            case HOLD -> 2;
            case NO_ACTION -> 3;
        };
    }
}
