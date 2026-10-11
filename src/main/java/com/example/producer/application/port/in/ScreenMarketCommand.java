package com.example.producer.application.port.in;

import com.example.producer.domain.strategy.InstrumentHistory;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record ScreenMarketCommand(LocalDate asOfSession, List<InstrumentHistory> instruments) {
    public ScreenMarketCommand {
        Objects.requireNonNull(asOfSession, "asOfSession must not be null");
        instruments = List.copyOf(Objects.requireNonNull(instruments, "instruments must not be null"));
        if (instruments.isEmpty()) {
            throw new IllegalArgumentException("At least one instrument is required");
        }
    }
}
