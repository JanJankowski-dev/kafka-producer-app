package com.example.producer.application.service;

import com.example.producer.application.port.in.AvailableMarketScreeningResult;
import com.example.producer.application.port.out.MarketDataSourcePort;
import com.example.producer.application.port.out.MarketResearchStorePort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MarketResearchService {
    private final MarketDataSourcePort marketData;
    private final AvailableMarketScreeningService screening;
    private final MarketResearchStorePort store;

    public MarketResearchService(MarketDataSourcePort marketData,
                                 AvailableMarketScreeningService screening,
                                 MarketResearchStorePort store) {
        this.marketData = marketData;
        this.screening = screening;
        this.store = store;
    }

    public SavedScreening run() {
        var snapshot = marketData.loadSnapshot();
        var result = screening.screenSnapshot(snapshot);
        UUID runId = store.save(snapshot, result);
        return new SavedScreening(runId, result);
    }

    public record SavedScreening(UUID runId, AvailableMarketScreeningResult screening) {
    }
}
