package com.example.producer.configuration;

import com.example.producer.application.port.in.ProposeOrderUseCase;
import com.example.producer.application.port.out.PublishOrderProposalPort;
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
            PublishOrderProposalPort publisher,
            Clock clock,
            Supplier<UUID> idGenerator
    ) {
        return new OrderProposalService(publisher, clock, idGenerator);
    }
}
