package com.example.producer.application.service;

import com.example.producer.application.port.out.PaperPortfolioStorePort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaperPortfolioService {
    private final PaperPortfolioStorePort store;

    public PaperPortfolioService(PaperPortfolioStorePort store) {
        this.store = store;
    }

    public UUID create() {
        return store.create();
    }

    public PaperPortfolioStorePort.PortfolioView view(UUID id) {
        return store.view(id);
    }

    public PaperPortfolioStorePort.TradeView trade(UUID id, PaperPortfolioStorePort.TradeCommand command) {
        return store.trade(id, command);
    }
}
