package com.example.producer.adapter.in.web;

import com.example.producer.application.service.DailyMarketResearchService;
import com.example.producer.application.service.MarketDataUnavailableException;
import com.example.producer.application.service.PaperDailyValuationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/daily-research")
public class DailyMarketResearchController {
    private final DailyMarketResearchService daily;
    private final PaperDailyValuationService valuations;
    private final boolean localMode;

    public DailyMarketResearchController(DailyMarketResearchService daily,
                                         PaperDailyValuationService valuations,
                                         @Value("${app.market-data.local-mode:false}") boolean localMode) {
        this.daily = daily;
        this.valuations = valuations;
        this.localMode = localMode;
    }

    @GetMapping("/valuations")
    public ResponseEntity<?> valuations() {
        if (!localMode) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(valuations.history());
    }

    @PostMapping("/run")
    public ResponseEntity<?> run() {
        if (!localMode) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok(daily.run());
        } catch (MarketDataUnavailableException exception) {
            return ResponseEntity.status(503).body(new ApiErrorResponse("MARKET_DATA_UNAVAILABLE",
                    exception.getMessage()));
        }
    }
}
