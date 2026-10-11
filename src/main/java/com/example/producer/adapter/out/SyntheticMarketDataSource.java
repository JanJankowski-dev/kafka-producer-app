package com.example.producer.adapter.out;

import com.example.producer.application.port.out.MarketDataSnapshot;
import com.example.producer.application.port.out.MarketDataSourcePort;
import com.example.producer.application.port.out.MarketInstrument;
import com.example.producer.domain.strategy.AssetType;
import com.example.producer.domain.strategy.DailyClose;
import com.example.producer.domain.strategy.InstrumentHistory;
import com.example.producer.domain.strategy.OpenPosition;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

@Component
@Profile("synthetic-demo")
public class SyntheticMarketDataSource implements MarketDataSourcePort {
    private static final LocalDate AS_OF_SESSION = LocalDate.of(2026, 10, 2);
    private static final List<LocalDate> SESSIONS = weekdaysEndingAt(AS_OF_SESSION, 21);

    @Override
    public MarketDataSnapshot loadSnapshot() {
        return new MarketDataSnapshot(
                "synthetic-gpw-us-2026-10-02-v1",
                AS_OF_SESSION,
                true,
                List.of(
                        instrument("GPW", "GPW-STOCK-DEMO", "PLN",
                                "11111111-1111-4111-8111-111111111111", AssetType.STOCK,
                                index -> 100 + index, null),
                        instrument("GPW", "GPW-ETF-DEMO", "PLN",
                                "22222222-2222-4222-8222-222222222222", AssetType.ETF,
                                index -> index == 20 ? 92 : 80 + index, null),
                        instrument("US", "US-STOCK-DEMO", "USD",
                                "33333333-3333-4333-8333-333333333333", AssetType.STOCK,
                                index -> 60 + index, new OpenPosition(20)),
                        instrument("US", "US-ETF-DEMO", "USD",
                                "44444444-4444-4444-8444-444444444444", AssetType.ETF,
                                index -> 100 + 2 * index, null)
                )
        );
    }

    private MarketInstrument instrument(
            String market,
            String symbol,
            String currency,
            String instrumentId,
            AssetType assetType,
            IntUnaryOperator priceAt,
            OpenPosition position
    ) {
        var closes = new ArrayList<DailyClose>();
        for (int index = 0; index < SESSIONS.size(); index++) {
            closes.add(new DailyClose(SESSIONS.get(index),
                    BigDecimal.valueOf(priceAt.applyAsInt(index))));
        }
        return new MarketInstrument(market, symbol, currency,
                new InstrumentHistory(UUID.fromString(instrumentId), assetType, closes, position));
    }

    private static List<LocalDate> weekdaysEndingAt(LocalDate lastSession, int count) {
        var dates = new ArrayList<LocalDate>();
        for (LocalDate date = lastSession; dates.size() < count; date = date.minusDays(1)) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY
                    && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                dates.add(date);
            }
        }
        Collections.reverse(dates);
        return List.copyOf(dates);
    }
}
