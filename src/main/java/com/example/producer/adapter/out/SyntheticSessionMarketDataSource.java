package com.example.producer.adapter.out;

import com.example.producer.application.port.out.MarketDataSnapshot;
import com.example.producer.application.port.out.MarketInstrument;
import com.example.producer.application.port.out.SessionMarketDataSourcePort;
import com.example.producer.domain.strategy.AssetType;
import com.example.producer.domain.strategy.DailyClose;
import com.example.producer.domain.strategy.InstrumentHistory;
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
public class SyntheticSessionMarketDataSource implements SessionMarketDataSourcePort {
    private static final List<LocalDate> SESSIONS = weekdaysEndingAt(LocalDate.of(2026, 10, 2), 50);

    @Override
    public List<LocalDate> availableSessions() {
        return SESSIONS;
    }

    @Override
    public MarketDataSnapshot loadSession(LocalDate session) {
        int index = SESSIONS.indexOf(session);
        if (index < 0) {
            throw new IllegalArgumentException("Session is not available in the synthetic fixture");
        }
        return new MarketDataSnapshot("synthetic-session-" + session + "-v2", session, true,
                List.of(
                        instrument("GPW", "GPW-STOCK-DEMO", "PLN",
                                "11111111-1111-4111-8111-111111111111", AssetType.STOCK,
                                index, day -> 100 + day),
                        instrument("GPW", "GPW-ETF-DEMO", "PLN",
                                "22222222-2222-4222-8222-222222222222", AssetType.ETF,
                                index, day -> 80 + day),
                        instrument("US", "US-STOCK-DEMO", "USD",
                                "33333333-3333-4333-8333-333333333333", AssetType.STOCK,
                                index, day -> 60 + day),
                        instrument("US", "US-ETF-DEMO", "USD",
                                "44444444-4444-4444-8444-444444444444", AssetType.ETF,
                                index, day -> day == 46 ? 130 : 100 + 2 * day)
                ));
    }

    private MarketInstrument instrument(String market, String symbol, String currency,
                                        String instrumentId, AssetType type, int latestIndex,
                                        IntUnaryOperator priceAt) {
        var closes = new ArrayList<DailyClose>();
        for (int index = Math.max(0, latestIndex - 20); index <= latestIndex; index++) {
            closes.add(new DailyClose(SESSIONS.get(index), BigDecimal.valueOf(priceAt.applyAsInt(index))));
        }
        return new MarketInstrument(market, symbol, currency,
                new InstrumentHistory(UUID.fromString(instrumentId), type, closes, null));
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
