package com.example.producer.adapter.out.kafka;

import com.example.producer.adapter.out.messaging.OrderProposedV1;
import com.example.producer.application.port.out.PendingOutboxEvent;
import com.example.producer.application.port.out.PublishOutboxEventPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@Slf4j
public class KafkaOutboxEventPublisher implements PublishOutboxEventPort {
    private static final String SUPPORTED_EVENT_TYPE = "OrderProposedV1";

    private final KafkaTemplate<String, OrderProposedV1> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaOutboxEventPublisher(
            KafkaTemplate<String, OrderProposedV1> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topic}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public CompletionStage<Void> publish(PendingOutboxEvent event) {
        try {
            var contract = deserializeAndValidate(event);
            return kafkaTemplate.send(topic, event.partitionKey(), contract)
                    .thenAccept(result -> log.info(
                            "Outbox event published eventId={} partition={} offset={}",
                            event.eventId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()
                    ));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private OrderProposedV1 deserializeAndValidate(PendingOutboxEvent event) {
        if (!SUPPORTED_EVENT_TYPE.equals(event.eventType())) {
            throw new IllegalArgumentException("Unsupported outbox event type: " + event.eventType());
        }

        try {
            var contract = objectMapper.readValue(event.payload(), OrderProposedV1.class);
            if (!event.eventId().equals(contract.eventId())) {
                throw new IllegalArgumentException("Outbox eventId does not match payload");
            }
            if (event.schemaVersion() != contract.schemaVersion()) {
                throw new IllegalArgumentException("Outbox schemaVersion does not match payload");
            }
            if (!event.partitionKey().equals(contract.portfolioId().toString())) {
                throw new IllegalArgumentException("Outbox partitionKey does not match payload");
            }
            return contract;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid outbox payload", exception);
        }
    }
}
