package com.example.producer.adapter.in.web;

import com.example.producer.application.port.in.MarketScreeningResult;
import com.example.producer.domain.strategy.TradingSignal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MarketScreeningResponse(boolean researchOnly, List<SignalResponse> signals) {
    static MarketScreeningResponse from(MarketScreeningResult result) {
        return new MarketScreeningResponse(true, result.signals().stream()
                .map(SignalResponse::from)
                .toList());
    }

    public record SignalResponse(
            UUID instrumentId,
            String assetType,
            LocalDate asOfSession,
            String strategyVersion,
            String action,
            String reasonCode,
            BigDecimal latestAdjustedClose,
            BigDecimal returnOver20SessionsPct,
            int maxHoldingSessions
    ) {
        static SignalResponse from(TradingSignal signal) {
            return new SignalResponse(
                    signal.instrumentId(),
                    signal.assetType().name(),
                    signal.asOfSession(),
                    signal.strategyVersion(),
                    signal.action().name(),
                    signal.reasonCode(),
                    signal.latestAdjustedClose(),
                    signal.returnOver20SessionsPct(),
                    signal.maxHoldingSessions()
            );
        }
    }
}
