package com.example.producer.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface HistoricalPricesPort {
    boolean configured();
    List<DailyPrice> fetch(String symbol, LocalDate startDate, LocalDate endDate);

    record DailyPrice(LocalDate sessionDate, BigDecimal rawOpen, BigDecimal rawClose,
                      BigDecimal adjustedClose, long volume, BigDecimal splitFactor,
                      BigDecimal dividendCash) {
        public DailyPrice(LocalDate sessionDate, BigDecimal rawClose,
                          BigDecimal adjustedClose, long volume) {
            this(sessionDate, rawClose, rawClose, adjustedClose, volume,
                    BigDecimal.ONE, BigDecimal.ZERO);
        }

        public DailyPrice {
            if (sessionDate == null || rawOpen == null || rawClose == null || adjustedClose == null
                    || splitFactor == null || dividendCash == null || rawOpen.signum() <= 0
                    || rawClose.signum() <= 0 || adjustedClose.signum() <= 0 || volume < 0
                    || splitFactor.signum() <= 0 || dividendCash.signum() < 0) {
                throw new IllegalArgumentException("Invalid daily market price");
            }
        }
    }
}
