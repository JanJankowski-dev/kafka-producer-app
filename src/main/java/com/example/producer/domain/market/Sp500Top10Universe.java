package com.example.producer.domain.market;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class Sp500Top10Universe {
    public static final LocalDate AS_OF = LocalDate.of(2026, 8, 31);
    public static final String VERSION = "SP500_TOP10_WEIGHT_2026_08_31";
    public static final String SOURCE_URL = "https://www.spglobal.com/spdji/en/indices/equity/sp-500/";

    private static final List<Instrument> INSTRUMENTS = List.of(
            instrument("NVDA", "Nvidia"),
            instrument("AAPL", "Apple"),
            instrument("MSFT", "Microsoft"),
            instrument("AMZN", "Amazon"),
            instrument("GOOGL", "Alphabet, class A"),
            instrument("AVGO", "Broadcom"),
            instrument("GOOG", "Alphabet, class C"),
            instrument("META", "Meta Platforms"),
            instrument("MU", "Micron Technology"),
            instrument("TSLA", "Tesla")
    );

    private Sp500Top10Universe() {
    }

    public static List<Instrument> instruments() {
        return INSTRUMENTS;
    }

    private static Instrument instrument(String symbol, String name) {
        UUID id = UUID.nameUUIDFromBytes(("US-EQUITY:" + symbol).getBytes(StandardCharsets.UTF_8));
        return new Instrument(id, symbol, name, "US", "USD");
    }

    public record Instrument(UUID instrumentId, String symbol, String name,
                             String market, String currency) {
    }
}
