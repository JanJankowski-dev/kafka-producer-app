package com.example.producer.domain.strategy;

public record OpenPosition(int heldSessions) {
    public OpenPosition {
        if (heldSessions < 0) {
            throw new IllegalArgumentException("heldSessions must not be negative");
        }
    }
}
