package com.example.producer.domain.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class Breakout20Strategy {
    public static final String VERSION = "BREAKOUT_20_V1";
    public static final int MAX_HOLDING_SESSIONS = 20;

    private static final int ENTRY_LOOKBACK_SESSIONS = 20;
    private static final int EXIT_AVERAGE_SESSIONS = 10;

    public TradingSignal evaluate(InstrumentHistory instrument, LocalDate asOfSession) {
        Objects.requireNonNull(instrument, "instrument must not be null");
        Objects.requireNonNull(asOfSession, "asOfSession must not be null");
        if (instrument.closes().stream().anyMatch(close -> close.sessionDate().isAfter(asOfSession))) {
            throw new IllegalArgumentException("Market history cannot contain future sessions");
        }

        List<DailyClose> closes = instrument.closes();
        if (closes.isEmpty() || !closes.get(closes.size() - 1).sessionDate().equals(asOfSession)) {
            return signal(instrument, asOfSession, SignalAction.NO_ACTION,
                    "NO_CLOSE_FOR_SESSION", null, null);
        }

        BigDecimal latest = closes.get(closes.size() - 1).adjustedClose();
        if (instrument.openPosition() != null) {
            if (instrument.openPosition().heldSessions() >= MAX_HOLDING_SESSIONS) {
                return signal(instrument, asOfSession, SignalAction.SELL,
                        "MAX_HOLDING_PERIOD", latest, null);
            }
            if (closes.size() < EXIT_AVERAGE_SESSIONS) {
                return signal(instrument, asOfSession, SignalAction.NO_ACTION,
                        "INSUFFICIENT_EXIT_HISTORY", latest, null);
            }
            BigDecimal average = closes.subList(closes.size() - EXIT_AVERAGE_SESSIONS, closes.size())
                    .stream()
                    .map(DailyClose::adjustedClose)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(EXIT_AVERAGE_SESSIONS), 8, RoundingMode.HALF_UP);
            return latest.compareTo(average) < 0
                    ? signal(instrument, asOfSession, SignalAction.SELL,
                            "BELOW_10_SESSION_AVERAGE", latest, null)
                    : signal(instrument, asOfSession, SignalAction.HOLD,
                            "EXIT_NOT_TRIGGERED", latest, null);
        }

        if (closes.size() < ENTRY_LOOKBACK_SESSIONS + 1) {
            return signal(instrument, asOfSession, SignalAction.NO_ACTION,
                    "INSUFFICIENT_ENTRY_HISTORY", latest, null);
        }
        BigDecimal previousHigh = closes.subList(
                        closes.size() - ENTRY_LOOKBACK_SESSIONS - 1, closes.size() - 1)
                .stream()
                .map(DailyClose::adjustedClose)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        BigDecimal first = closes.get(closes.size() - ENTRY_LOOKBACK_SESSIONS - 1).adjustedClose();
        BigDecimal returnPct = latest.subtract(first)
                .multiply(BigDecimal.valueOf(100))
                .divide(first, 4, RoundingMode.HALF_UP);

        return latest.compareTo(previousHigh) > 0
                ? signal(instrument, asOfSession, SignalAction.BUY,
                        "ABOVE_PREVIOUS_20_SESSION_HIGH", latest, returnPct)
                : signal(instrument, asOfSession, SignalAction.NO_ACTION,
                        "NO_BREAKOUT", latest, returnPct);
    }

    private TradingSignal signal(
            InstrumentHistory instrument,
            LocalDate asOfSession,
            SignalAction action,
            String reasonCode,
            BigDecimal latest,
            BigDecimal returnPct
    ) {
        return new TradingSignal(instrument.instrumentId(), instrument.assetType(), asOfSession,
                VERSION, action, reasonCode, latest, returnPct, MAX_HOLDING_SESSIONS);
    }
}
