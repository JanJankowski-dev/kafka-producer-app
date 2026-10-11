package com.example.producer;

import com.example.producer.application.port.out.HistoricalPricesPort.DailyPrice;
import com.example.producer.application.port.out.MarketImportStorePort;
import com.example.producer.application.service.MarketSignalHistoryService;
import com.example.producer.domain.market.Sp500Top10Universe;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketSignalHistoryServiceTest {
    @Test
    void calculatesPastSignalsWithoutTreatingForwardCloseAsTradeProfit() {
        var store = mock(MarketImportStorePort.class);
        var instrument = Sp500Top10Universe.instruments().get(0);
        LocalDate start = LocalDate.of(2026, 1, 1);
        var prices = new ArrayList<DailyPrice>();
        for (int day = 0; day < 26; day++) {
            BigDecimal close = BigDecimal.valueOf(100 + day);
            prices.add(new DailyPrice(start.plusDays(day), close, close, 1000));
        }
        UUID batchId = UUID.randomUUID();
        var status = new MarketImportStorePort.ImportStatus(batchId, "TIINGO_EOD",
                Sp500Top10Universe.VERSION, Sp500Top10Universe.AS_OF,
                start.plusDays(25), OffsetDateTime.now(), 1, 26);
        when(store.latestHistories()).thenReturn(new MarketImportStorePort.ImportedBatch(
                status, List.of(new MarketImportStorePort.ImportedHistory(instrument, prices))));

        var result = new MarketSignalHistoryService(store).history();

        assertThat(result.researchOnly()).isTrue();
        assertThat(result.buyObservations()).hasSize(6);
        assertThat(result.buyObservations().get(0).forwardAdjustedCloseReturnPct()).isNull();
        assertThat(result.buyObservations().get(5).forwardAdjustedCloseReturnPct())
                .isEqualByComparingTo("4.1667");
    }
}
