package com.example.producer.application.port.out;

import java.time.LocalDate;
import java.util.List;

public interface SessionMarketDataSourcePort {
    List<LocalDate> availableSessions();
    MarketDataSnapshot loadSession(LocalDate session);
}
