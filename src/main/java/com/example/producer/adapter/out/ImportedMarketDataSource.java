package com.example.producer.adapter.out;

import com.example.producer.application.port.out.MarketDataSnapshot;
import com.example.producer.application.port.out.MarketDataSourcePort;
import com.example.producer.application.port.out.MarketImportStorePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!synthetic-demo")
public class ImportedMarketDataSource implements MarketDataSourcePort {
    private final MarketImportStorePort store;

    public ImportedMarketDataSource(MarketImportStorePort store) {
        this.store = store;
    }

    @Override
    public MarketDataSnapshot loadSnapshot() {
        return store.latestSnapshot();
    }
}
