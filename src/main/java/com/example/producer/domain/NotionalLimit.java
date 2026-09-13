package com.example.producer.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record NotionalLimit(BigDecimal amount, Currency currency) {
    public NotionalLimit {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
    }
}
