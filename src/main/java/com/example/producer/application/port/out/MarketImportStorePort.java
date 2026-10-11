package com.example.producer.application.port.out;

import com.example.producer.application.port.out.HistoricalPricesPort.DailyPrice;
import com.example.producer.domain.market.Sp500Top10Universe.Instrument;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface MarketImportStorePort {
    ImportStatus save(String contentHash, LocalDate latestSession, int sessionCount,
                      List<ImportedHistory> histories);
    ImportStatus latestStatus();
    MarketDataSnapshot latestSnapshot();
    ImportedBatch latestHistories();

    record ImportedBatch(ImportStatus status, List<ImportedHistory> histories) {
        public ImportedBatch {
            histories = List.copyOf(histories);
        }
    }

    record ImportedHistory(Instrument instrument, List<DailyPrice> prices) {
        public ImportedHistory {
            prices = List.copyOf(prices);
        }
    }

    record ImportStatus(UUID batchId, String source, String universeVersion,
                        LocalDate universeAsOf, LocalDate latestCommonSession,
                        OffsetDateTime importedAt, int instrumentCount, int sessionCount) {
    }
}
