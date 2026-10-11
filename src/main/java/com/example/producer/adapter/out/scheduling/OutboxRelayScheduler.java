package com.example.producer.adapter.out.scheduling;

import com.example.producer.application.service.OutboxRelayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true")
@Slf4j
public class OutboxRelayScheduler {
    private final OutboxRelayService relay;

    public OutboxRelayScheduler(OutboxRelayService relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms}")
    public void publishPendingEvents() {
        int count = relay.relayBatch();
        if (count > 0) {
            log.info("Outbox relay processed events count={}", count);
        }
    }
}
