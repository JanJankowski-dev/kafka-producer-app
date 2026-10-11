package com.example.producer.adapter.in.web;

import com.example.producer.application.port.in.ScreenAvailableMarketUseCase;
import com.example.producer.application.port.in.ScreenMarketUseCase;
import com.example.producer.application.service.MarketResearchService;
import com.example.producer.application.service.MarketDataUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/market-screenings")
public class MarketScreeningController {
    private final ScreenMarketUseCase useCase;
    private final ScreenAvailableMarketUseCase availableMarket;
    private final MarketResearchService research;
    private final boolean localMode;

    public MarketScreeningController(
            ScreenMarketUseCase useCase,
            ScreenAvailableMarketUseCase availableMarket,
            MarketResearchService research,
            @Value("${app.market-data.local-mode:false}") boolean localMode
    ) {
        this.useCase = useCase;
        this.availableMarket = availableMarket;
        this.research = research;
        this.localMode = localMode;
    }

    @GetMapping("/latest")
    public ResponseEntity<?> latest() {
        if (!localMode) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok(AvailableMarketScreeningResponse.from(
                    availableMarket.screenAvailableMarket()));
        } catch (MarketDataUnavailableException exception) {
            return ResponseEntity.status(503).body(new ApiErrorResponse("MARKET_DATA_UNAVAILABLE",
                    exception.getMessage()));
        }
    }

    @PostMapping("/runs")
    public ResponseEntity<?> saveRun() {
        if (!localMode) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok(SavedScreeningResponse.from(research.run()));
        } catch (MarketDataUnavailableException exception) {
            return ResponseEntity.status(503).body(new ApiErrorResponse("MARKET_DATA_UNAVAILABLE",
                    exception.getMessage()));
        }
    }

    @PostMapping("/preview")
    public ResponseEntity<?> preview(@Valid @RequestBody MarketScreeningRequest request) {
        try {
            return ResponseEntity.ok(MarketScreeningResponse.from(useCase.screen(request.toCommand())));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(
                    new ApiErrorResponse("INVALID_MARKET_SCREENING", exception.getMessage())
            );
        }
    }
}
