package com.example.producer.adapter.in.web;

import com.example.producer.application.service.MarketDataUnavailableException;
import com.example.producer.application.service.StrategyComparisonService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/strategy-comparisons")
public class StrategyComparisonController {
    private final StrategyComparisonService service;
    private final boolean localMode;

    public StrategyComparisonController(StrategyComparisonService service,
                                        @Value("${app.market-data.local-mode:false}") boolean localMode) {
        this.service = service;
        this.localMode = localMode;
    }

    @GetMapping("/latest")
    public ResponseEntity<?> latest() {
        if (!localMode) return ResponseEntity.notFound().build();
        var comparison = service.latest();
        return comparison == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(comparison);
    }

    @PostMapping
    public ResponseEntity<?> create() {
        if (!localMode) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok(service.createForLatestBatch());
        } catch (MarketDataUnavailableException exception) {
            return ResponseEntity.status(503).body(new ApiErrorResponse("MARKET_DATA_UNAVAILABLE",
                    exception.getMessage()));
        }
    }
}
