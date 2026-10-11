package com.example.producer.domain.strategy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TradingSignal(
        UUID instrumentId,
        AssetType assetType,
        LocalDate asOfSession,
        String strategyVersion,
        SignalAction action,
        String reasonCode,
        BigDecimal latestAdjustedClose,
        BigDecimal returnOver20SessionsPct,
        int maxHoldingSessions
) {
}
