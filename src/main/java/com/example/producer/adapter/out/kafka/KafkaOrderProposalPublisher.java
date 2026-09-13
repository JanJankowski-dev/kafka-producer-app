package com.example.producer.adapter.out.kafka;

import com.example.producer.application.port.out.PublishOrderProposalPort;
import com.example.producer.domain.OrderProposalProposed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@Slf4j
public class KafkaOrderProposalPublisher implements PublishOrderProposalPort {
    private final KafkaTemplate<String, OrderProposedV1> kafkaTemplate;
    private final String topic;

    public KafkaOrderProposalPublisher(
            KafkaTemplate<String, OrderProposedV1> kafkaTemplate,
            @Value("${app.kafka.topic}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public CompletionStage<Void> publish(OrderProposalProposed event) {
        var contract = toContract(event);
        String key = event.proposal().portfolioId().value().toString();
        try {
            return kafkaTemplate.send(topic, key, contract)
                    .thenAccept(result -> log.info(
                            "Order proposal published eventId={} proposalId={} partition={} offset={}",
                            event.eventId(),
                            event.proposal().proposalId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()
                    ));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private OrderProposedV1 toContract(OrderProposalProposed event) {
        var proposal = event.proposal();
        return new OrderProposedV1(
                event.eventId(),
                proposal.proposalId(),
                event.occurredAt(),
                event.schemaVersion(),
                proposal.portfolioId().value(),
                proposal.instrumentId().value(),
                proposal.side().name(),
                proposal.maxNotional().amount(),
                proposal.maxNotional().currency().getCurrencyCode(),
                proposal.strategySource().name(),
                proposal.rationale(),
                proposal.evidenceSources(),
                proposal.confidence().value(),
                proposal.validUntil()
        );
    }
}
