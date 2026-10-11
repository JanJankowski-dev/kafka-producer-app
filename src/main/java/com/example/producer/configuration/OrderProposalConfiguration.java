package com.example.producer.configuration;

import com.example.producer.application.port.in.ProposeOrderUseCase;
import com.example.producer.application.port.out.AcceptOrderProposalPort;
import com.example.producer.application.service.OrderProposalService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

@Configuration
public class OrderProposalConfiguration {
    @Bean
    ProposeOrderUseCase proposeOrderUseCase(
            AcceptOrderProposalPort store,
            Clock clock,
            Supplier<UUID> idGenerator
    ) {
        return new OrderProposalService(store, clock, idGenerator);
    }
}
