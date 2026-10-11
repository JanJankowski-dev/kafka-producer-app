package com.example.producer.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface PaperDailyValuationStorePort {
    List<Valuation> savePair(UUID comparisonId, UUID batchId, LocalDate session,
                             UUID traditionalPortfolioId, Mark traditional,
                             UUID aiPortfolioId, Mark ai);
    List<Valuation> history(UUID comparisonId);

    record Mark(BigDecimal cashUsd, BigDecimal positionsUsd) { }

    record Valuation(UUID comparisonId, UUID portfolioId, UUID marketImportBatchId,
                     LocalDate session, BigDecimal cashUsd, BigDecimal positionsUsd,
                     BigDecimal equityUsd, OffsetDateTime recordedAt) {
    }
}
