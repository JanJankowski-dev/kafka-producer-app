package com.example.producer.adapter.in.web;

import com.example.producer.application.port.in.ProposeOrderCommand;
import com.example.producer.domain.Confidence;
import com.example.producer.domain.InstrumentId;
import com.example.producer.domain.NotionalLimit;
import com.example.producer.domain.OrderSide;
import com.example.producer.domain.PortfolioId;
import com.example.producer.domain.ProposalSource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

public record CreateOrderProposalRequest(
        @NotNull UUID portfolioId,
        @NotNull UUID instrumentId,
        @NotNull OrderSide side,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal maxNotional,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull ProposalSource strategySource,
        @NotBlank @Size(max = 2000) String rationale,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 2048) String> evidenceSources,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal confidence,
        @NotNull @Future Instant validUntil
) {
    ProposeOrderCommand toCommand() {
        return new ProposeOrderCommand(
                new PortfolioId(portfolioId),
                new InstrumentId(instrumentId),
                side,
                new NotionalLimit(maxNotional, Currency.getInstance(currency)),
                strategySource,
                rationale,
                evidenceSources,
                new Confidence(confidence),
                validUntil
        );
    }
}
