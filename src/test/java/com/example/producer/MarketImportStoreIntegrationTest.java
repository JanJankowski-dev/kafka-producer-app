package com.example.producer;

import com.example.producer.application.port.out.HistoricalPricesPort.DailyPrice;
import com.example.producer.application.port.out.MarketImportStorePort;
import com.example.producer.application.port.out.MarketImportStorePort.ImportedHistory;
import com.example.producer.configuration.PostgresIntegrationTest;
import com.example.producer.domain.market.Sp500Top10Universe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresIntegrationTest
class MarketImportStoreIntegrationTest {
    @Autowired private MarketImportStorePort store;

    @Test
    void persistsTenRealSymbolsAndReturnsOnlyImportedPrices() {
        LocalDate first = LocalDate.of(2026, 8, 1);
        var histories = new ArrayList<ImportedHistory>();
        for (var instrument : Sp500Top10Universe.instruments()) {
            var prices = new ArrayList<DailyPrice>();
            for (int index = 0; index < 22; index++) {
                prices.add(new DailyPrice(first.plusDays(index), BigDecimal.valueOf(100 + index),
                        BigDecimal.valueOf(90 + index), 1000));
            }
            histories.add(new ImportedHistory(instrument, prices));
        }
        var firstSave = store.save("a".repeat(64), first.plusDays(21), 22, histories);
        var repeat = store.save("a".repeat(64), first.plusDays(21), 22, histories);

        assertThat(repeat.batchId()).isEqualTo(firstSave.batchId());
        var snapshot = store.latestSnapshot();
        assertThat(snapshot.synthetic()).isFalse();
        assertThat(snapshot.instruments()).hasSize(10);
        assertThat(snapshot.instruments().get(0).symbol()).isEqualTo("NVDA");
        assertThat(snapshot.instruments().get(0).history().closes()).hasSize(21);
        assertThat(snapshot.instruments().get(0).history().closes().get(20).adjustedClose())
                .isEqualByComparingTo("111");
        var imported = store.latestHistories();
        assertThat(imported.status().batchId()).isEqualTo(firstSave.batchId());
        assertThat(imported.histories()).hasSize(10);
        assertThat(imported.histories().get(0).prices()).hasSize(22);
        assertThat(imported.histories().get(0).prices().get(0).rawClose())
                .isEqualByComparingTo("100");
    }
}
