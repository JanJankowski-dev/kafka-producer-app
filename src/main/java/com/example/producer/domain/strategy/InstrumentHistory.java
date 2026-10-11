package com.example.producer.domain.strategy;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record InstrumentHistory(
        UUID instrumentId,
        AssetType assetType,
        List<DailyClose> closes,
        OpenPosition openPosition
) {
    public InstrumentHistory {
        Objects.requireNonNull(instrumentId, "instrumentId must not be null");
        Objects.requireNonNull(assetType, "assetType must not be null");
        closes = List.copyOf(Objects.requireNonNull(closes, "closes must not be null"));
        for (int index = 1; index < closes.size(); index++) {
            if (!closes.get(index).sessionDate().isAfter(closes.get(index - 1).sessionDate())) {
                throw new IllegalArgumentException("Daily closes must have unique, ascending session dates");
            }
        }
    }
}
