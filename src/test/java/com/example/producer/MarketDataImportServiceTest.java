package com.example.producer;

import com.example.producer.application.port.out.HistoricalPricesPort;
import com.example.producer.application.port.out.HistoricalPricesPort.DailyPrice;
import com.example.producer.application.port.out.MarketImportStorePort;
import com.example.producer.application.service.MarketDataImportService;
import com.example.producer.application.service.MarketDataUnavailableException;
import com.example.producer.domain.market.Sp500Top10Universe;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketDataImportServiceTest {
    @Test
    void universeIsTheTenSeparateIndexConstituentsFromTheDatedSource() {
        assertThat(Sp500Top10Universe.instruments())
                .extracting(Sp500Top10Universe.Instrument::symbol)
                .containsExactly("NVDA", "AAPL", "MSFT", "AMZN", "GOOGL", "AVGO",
                        "GOOG", "META", "MU", "TSLA");
    }

    @Test
    void importsOnlyWhenAllTenHaveCommonRealSessions() {
        HistoricalPricesPort provider = mock(HistoricalPricesPort.class);
        MarketImportStorePort store = mock(MarketImportStorePort.class);
        when(provider.configured()).thenReturn(true);
        LocalDate first = LocalDate.now().minusDays(40);
        var prices = new ArrayList<DailyPrice>();
        for (int index = 0; index < 25; index++) {
            prices.add(new DailyPrice(first.plusDays(index), BigDecimal.valueOf(100 + index),
                    BigDecimal.valueOf(99 + index), 1000));
        }
        when(provider.fetch(anyString(), any(), any())).thenReturn(prices);
        new MarketDataImportService(provider, store).importLatest();
        verify(store).save(anyString(), any(), anyInt(), anyList());
    }

    @Test
    void noKeyNeverProducesInventedPrices() {
        HistoricalPricesPort provider = mock(HistoricalPricesPort.class);
        MarketImportStorePort store = mock(MarketImportStorePort.class);
        assertThatThrownBy(() -> new MarketDataImportService(provider, store).importLatest())
                .isInstanceOf(MarketDataUnavailableException.class);
    }
}
