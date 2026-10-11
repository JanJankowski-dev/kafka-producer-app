package com.example.producer.application.service;

import com.example.producer.application.port.out.MarketImportStorePort;
import com.example.producer.application.port.out.StrategyComparisonStorePort;
import com.example.producer.domain.strategy.Breakout20Strategy;
import org.springframework.stereotype.Service;

@Service
public class StrategyComparisonService {
    private final MarketImportStorePort imports;
    private final StrategyComparisonStorePort comparisons;

    public StrategyComparisonService(MarketImportStorePort imports,
                                     StrategyComparisonStorePort comparisons) {
        this.imports = imports;
        this.comparisons = comparisons;
    }

    public StrategyComparisonStorePort.ComparisonView createForLatestBatch() {
        var latest = imports.latestStatus();
        if (latest == null) {
            throw new MarketDataUnavailableException("Import real market data before creating portfolios");
        }
        return comparisons.createOrFind(latest.batchId(), Breakout20Strategy.VERSION);
    }

    public StrategyComparisonStorePort.ComparisonView latest() {
        return comparisons.latest();
    }
}
