package com.example.producer.application.port.out;

import com.example.producer.application.port.in.AvailableMarketScreeningResult;

import java.util.UUID;

public interface MarketResearchStorePort {
    UUID save(MarketDataSnapshot snapshot, AvailableMarketScreeningResult result);
    UUID saveForPortfolio(UUID portfolioId, MarketDataSnapshot marketSnapshot,
                          MarketDataSnapshot positionAwareSnapshot,
                          AvailableMarketScreeningResult result);
}
