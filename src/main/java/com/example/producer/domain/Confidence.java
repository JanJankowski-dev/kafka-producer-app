package com.example.producer.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record Confidence(BigDecimal value) {
    public Confidence {
        Objects.requireNonNull(value, "confidence must not be null");
        if (value.compareTo(BigDecimal.ZERO) < 0
                || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be between zero and one");
        }
    }
}
