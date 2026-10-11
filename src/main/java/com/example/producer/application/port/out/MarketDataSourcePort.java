package com.example.producer.application.port.out;

public interface MarketDataSourcePort {
    MarketDataSnapshot loadSnapshot();
}
