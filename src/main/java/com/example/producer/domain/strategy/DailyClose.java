package com.example.producer.domain.strategy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record DailyClose(LocalDate sessionDate, BigDecimal adjustedClose) {
    public DailyClose {
        Objects.requireNonNull(sessionDate, "sessionDate must not be null");
        Objects.requireNonNull(adjustedClose, "adjustedClose must not be null");
        if (adjustedClose.signum() <= 0) {
            throw new IllegalArgumentException("adjustedClose must be greater than zero");
        }
    }
}
