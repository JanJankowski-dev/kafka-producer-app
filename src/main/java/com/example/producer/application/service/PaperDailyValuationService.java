package com.example.producer.application.service;

import com.example.producer.application.port.out.PaperDailyValuationStorePort;
import com.example.producer.application.port.out.PaperPortfolioStorePort;
import com.example.producer.application.port.out.StrategyComparisonStorePort;
import com.example.producer.application.port.out.MarketImportStorePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PaperDailyValuationService {
    private final StrategyComparisonStorePort comparisons;
    private final PaperPortfolioStorePort portfolios;
    private final PaperDailyValuationStorePort valuations;
    private final MarketImportStorePort imports;

    public PaperDailyValuationService(StrategyComparisonStorePort comparisons,
                                      PaperPortfolioStorePort portfolios,
                                      PaperDailyValuationStorePort valuations,
                                      MarketImportStorePort imports) {
        this.comparisons = comparisons;
        this.portfolios = portfolios;
        this.valuations = valuations;
        this.imports = imports;
    }

    public List<PaperDailyValuationStorePort.Valuation> recordLatest(UUID batchId,
                                                                      LocalDate session) {
        var comparison = comparisons.latest();
        if (comparison == null) return List.of();
        var batch = imports.latestHistories();
        if (!batch.status().batchId().equals(batchId)
                || !batch.status().latestCommonSession().equals(session)) {
            throw new IllegalStateException("Valuation requires the latest imported batch");
        }
        return valuations.savePair(comparison.comparisonId(), batchId, session,
                comparison.traditionalPortfolioId(), markUsd(comparison.traditionalPortfolioId(), batch),
                comparison.aiPortfolioId(), markUsd(comparison.aiPortfolioId(), batch));
    }

    public List<PaperDailyValuationStorePort.Valuation> history() {
        var comparison = comparisons.latest();
        return comparison == null ? List.of() : valuations.history(comparison.comparisonId());
    }

    private PaperDailyValuationStorePort.Mark markUsd(UUID portfolioId,
                                                     MarketImportStorePort.ImportedBatch batch) {
        var view = portfolios.view(portfolioId);
        if (view.cash().size() != 1 || !view.cash().get(0).currency().equals("USD")) {
            throw new IllegalStateException("Comparison portfolio must contain USD cash only");
        }
        BigDecimal positionsUsd = BigDecimal.ZERO;
        for (var position : view.positions()) {
            if (!position.currency().equals("USD")) {
                throw new IllegalStateException("Non-USD position in comparison portfolio");
            }
            var history = batch.histories().stream()
                    .filter(h -> h.instrument().instrumentId().equals(position.instrumentId()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Missing position market data"));
            var price = history.prices().get(history.prices().size() - 1);
            positionsUsd = positionsUsd.add(position.quantity().multiply(price.rawClose()));
        }
        return new PaperDailyValuationStorePort.Mark(view.cash().get(0).balance(),
                positionsUsd.setScale(8, java.math.RoundingMode.HALF_UP));
    }
}
