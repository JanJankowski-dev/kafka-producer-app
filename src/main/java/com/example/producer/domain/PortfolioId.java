package com.example.producer.domain;

import java.util.Objects;
import java.util.UUID;

public record PortfolioId(UUID value) {
    public PortfolioId {
        Objects.requireNonNull(value, "portfolioId must not be null");
    }
}
