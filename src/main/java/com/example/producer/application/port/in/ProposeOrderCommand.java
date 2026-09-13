package com.example.producer.application.port.in;

import com.example.producer.domain.Confidence;
import com.example.producer.domain.InstrumentId;
import com.example.producer.domain.NotionalLimit;
import com.example.producer.domain.OrderSide;
import com.example.producer.domain.PortfolioId;
import com.example.producer.domain.ProposalSource;

import java.time.Instant;
import java.util.List;

public record ProposeOrderCommand(
        PortfolioId portfolioId,
        InstrumentId instrumentId,
        OrderSide side,
        NotionalLimit maxNotional,
        ProposalSource strategySource,
        String rationale,
        List<String> evidenceSources,
        Confidence confidence,
        Instant validUntil
) {
    public ProposeOrderCommand {
        evidenceSources = List.copyOf(evidenceSources);
    }
}
