package com.example.producer.configuration;

import com.example.producer.application.port.in.ScreenMarketUseCase;
import com.example.producer.application.port.out.MarketDataSourcePort;
import com.example.producer.application.service.AvailableMarketScreeningService;
import com.example.producer.application.service.MarketScreeningService;
import com.example.producer.domain.strategy.Breakout20Strategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MarketScreeningConfiguration {
    @Bean
    ScreenMarketUseCase screenMarketUseCase() {
        return new MarketScreeningService(new Breakout20Strategy());
    }

    @Bean
    AvailableMarketScreeningService screenAvailableMarketUseCase(
            MarketDataSourcePort marketData,
            ScreenMarketUseCase screening
    ) {
        return new AvailableMarketScreeningService(marketData, screening);
    }
}
