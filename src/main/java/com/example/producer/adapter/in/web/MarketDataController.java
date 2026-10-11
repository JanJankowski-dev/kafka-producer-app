package com.example.producer.adapter.in.web;

import com.example.producer.application.port.out.MarketImportStorePort;
import com.example.producer.application.service.MarketDataImportService;
import com.example.producer.application.service.MarketDataUnavailableException;
import com.example.producer.domain.market.Sp500Top10Universe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/market-data")
public class MarketDataController {
    private final MarketDataImportService imports;
    private final boolean localMode;

    public MarketDataController(MarketDataImportService imports,
                                @Value("${app.market-data.local-mode:false}") boolean localMode) {
        this.imports = imports;
        this.localMode = localMode;
    }

    @GetMapping("/universe")
    public UniverseResponse universe() {
        return new UniverseResponse(Sp500Top10Universe.VERSION, Sp500Top10Universe.AS_OF,
                Sp500Top10Universe.SOURCE_URL, Sp500Top10Universe.instruments());
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        if (!localMode) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new StatusResponse("EODHD_EOD", imports.configured(),
                imports.latestStatus()));
    }

    @PostMapping("/imports")
    public ResponseEntity<?> importPrices() {
        if (!localMode) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok(imports.importLatest());
        } catch (MarketDataUnavailableException exception) {
            return ResponseEntity.status(503).body(new ApiErrorResponse("MARKET_DATA_UNAVAILABLE",
                    exception.getMessage()));
        }
    }

    public record UniverseResponse(String version, LocalDate asOf, String sourceUrl,
                                   List<Sp500Top10Universe.Instrument> instruments) {
    }

    public record StatusResponse(String source, boolean apiKeyConfigured,
                                 MarketImportStorePort.ImportStatus latestImport) {
    }
}
