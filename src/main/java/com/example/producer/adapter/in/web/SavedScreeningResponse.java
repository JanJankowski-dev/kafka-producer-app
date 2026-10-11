package com.example.producer.adapter.in.web;

import com.example.producer.application.service.MarketResearchService.SavedScreening;

import java.util.UUID;

public record SavedScreeningResponse(UUID runId, AvailableMarketScreeningResponse screening) {
    static SavedScreeningResponse from(SavedScreening saved) {
        return new SavedScreeningResponse(saved.runId(), AvailableMarketScreeningResponse.from(saved.screening()));
    }
}
