package com.example.producer.adapter.out.scheduling;

import com.example.producer.application.service.DailyMarketResearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.market-data", name = {"daily-enabled", "local-mode"},
        havingValue = "true")
public class DailyMarketResearchScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(DailyMarketResearchScheduler.class);
    private final DailyMarketResearchService daily;

    public DailyMarketResearchScheduler(DailyMarketResearchService daily) {
        this.daily = daily;
    }

    @Scheduled(cron = "0 0 9 * * MON-SAT", zone = "Europe/Warsaw")
    public void run() {
        try {
            var result = daily.run();
            LOG.info("Daily market research: session={}, newBatch={}, newSession={}, runId={}",
                    result.session(), result.newBatch(), result.newSession(), result.screeningRunId());
        } catch (Exception exception) {
            LOG.error("Daily market research failed; previous data remains available", exception);
        }
    }
}
