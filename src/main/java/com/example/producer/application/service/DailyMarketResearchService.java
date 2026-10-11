package com.example.producer.application.service;

import com.example.producer.application.port.out.MarketImportStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class DailyMarketResearchService {
    private static final Logger LOG = LoggerFactory.getLogger(DailyMarketResearchService.class);
    private final MarketDataImportService imports;
    private final MarketResearchService research;
    private final PaperDailyValuationService valuations;
    private final AutoPaperTradingService paper;
    private final EtfBenchmarkService benchmarks;

    public DailyMarketResearchService(MarketDataImportService imports, MarketResearchService research,
                                      PaperDailyValuationService valuations,
                                      AutoPaperTradingService paper,
                                      EtfBenchmarkService benchmarks) {
        this.imports = imports;
        this.research = research;
        this.valuations = valuations;
        this.paper = paper;
        this.benchmarks = benchmarks;
    }

    public DailyCycleResult run() {
        MarketImportStorePort.ImportStatus previous = imports.latestStatus();
        MarketImportStorePort.ImportStatus current = imports.importLatest();
        MarketResearchService.SavedScreening screening = research.run();
        AutoPaperTradingService.Result paperResult = paper.runLatest();
        int valuationsRecorded = valuations.recordLatest(current.batchId(),
                current.latestCommonSession()).size();
        try {
            benchmarks.refresh();
        } catch (RuntimeException exception) {
            // Benchmarks are optional: a provider outage must not roll back paper decisions.
            LOG.warn("ETF market close benchmark refresh failed: {}", exception.toString());
        }
        return new DailyCycleResult(current.batchId(), current.latestCommonSession(),
                screening.runId(), previous == null || !previous.batchId().equals(current.batchId()),
                previous == null || current.latestCommonSession().isAfter(previous.latestCommonSession()),
                current.instrumentCount(), current.sessionCount(), valuationsRecorded,
                paperResult.traditionalDecisions(), paperResult.aiDecisions());
    }

    public record DailyCycleResult(UUID batchId, LocalDate session, UUID screeningRunId,
                                   boolean newBatch, boolean newSession,
                                   int instrumentCount, int sessionCount, int valuationsRecorded,
                                   int traditionalDecisions, int aiDecisions) {
    }
}
