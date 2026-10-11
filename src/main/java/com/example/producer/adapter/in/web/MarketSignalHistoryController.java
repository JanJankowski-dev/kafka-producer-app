package com.example.producer.adapter.in.web;

import com.example.producer.application.service.MarketDataUnavailableException;
import com.example.producer.application.service.MarketSignalHistoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/market-screenings/history")
public class MarketSignalHistoryController {
    private final MarketSignalHistoryService service;
    private final boolean localMode;

    public MarketSignalHistoryController(MarketSignalHistoryService service,
                                         @Value("${app.market-data.local-mode:false}") boolean localMode) {
        this.service = service;
        this.localMode = localMode;
    }

    @GetMapping
    public ResponseEntity<?> history() {
        if (!localMode) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok(service.history());
        } catch (MarketDataUnavailableException exception) {
            return ResponseEntity.status(503).body(new ApiErrorResponse("MARKET_DATA_UNAVAILABLE",
                    exception.getMessage()));
        }
    }
}
