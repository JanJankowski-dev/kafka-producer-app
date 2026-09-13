package com.example.producer.domain;

import java.util.Objects;
import java.util.UUID;

public record InstrumentId(UUID value) {
    public InstrumentId {
        Objects.requireNonNull(value, "instrumentId must not be null");
    }
}
