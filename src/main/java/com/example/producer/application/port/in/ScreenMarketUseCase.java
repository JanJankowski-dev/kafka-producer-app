package com.example.producer.application.port.in;

public interface ScreenMarketUseCase {
    MarketScreeningResult screen(ScreenMarketCommand command);
}
