package com.example.producer.adapter.in.web;

import com.example.producer.application.port.in.ScreenMarketCommand;
import com.example.producer.domain.strategy.AssetType;
import com.example.producer.domain.strategy.DailyClose;
import com.example.producer.domain.strategy.InstrumentHistory;
import com.example.producer.domain.strategy.OpenPosition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MarketScreeningRequest(
        @NotNull LocalDate asOfSession,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid InstrumentRequest> instruments
) {
    ScreenMarketCommand toCommand() {
        return new ScreenMarketCommand(asOfSession, instruments.stream()
                .map(InstrumentRequest::toDomain)
                .toList());
    }

    public record InstrumentRequest(
            @NotNull UUID instrumentId,
            @NotNull AssetType assetType,
            @NotNull @Size(max = 60) List<@NotNull @Valid CloseRequest> closes,
            @Min(0) Integer heldSessions
    ) {
        InstrumentHistory toDomain() {
            return new InstrumentHistory(
                    instrumentId,
                    assetType,
                    closes.stream().map(CloseRequest::toDomain).toList(),
                    heldSessions == null ? null : new OpenPosition(heldSessions)
            );
        }
    }

    public record CloseRequest(
            @NotNull LocalDate sessionDate,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal adjustedClose
    ) {
        DailyClose toDomain() {
            return new DailyClose(sessionDate, adjustedClose);
        }
    }
}
