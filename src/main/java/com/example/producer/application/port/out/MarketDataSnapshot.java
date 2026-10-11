package com.example.producer.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record MarketDataSnapshot(
        String snapshotId,
        LocalDate asOfSession,
        boolean synthetic,
        List<MarketInstrument> instruments
) {
    public MarketDataSnapshot {
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        Objects.requireNonNull(asOfSession, "asOfSession must not be null");
        instruments = List.copyOf(Objects.requireNonNull(instruments, "instruments must not be null"));
    }
}
