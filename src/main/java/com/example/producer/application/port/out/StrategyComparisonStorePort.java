package com.example.producer.application.port.out;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface StrategyComparisonStorePort {
    ComparisonView createOrFind(UUID marketImportBatchId, String traditionalStrategyVersion);
    ComparisonView findByBatch(UUID marketImportBatchId);
    ComparisonView latest();

    record ComparisonView(UUID comparisonId, UUID marketImportBatchId,
                          UUID traditionalPortfolioId, UUID aiPortfolioId,
                          String traditionalStrategyVersion, String aiModelVersion,
                          OffsetDateTime createdAt) {
    }
}
