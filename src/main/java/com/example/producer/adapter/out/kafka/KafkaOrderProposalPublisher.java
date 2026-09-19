package com.example.producer.adapter.out.kafka;

import com.example.producer.adapter.out.messaging.OrderProposedV1;
import com.example.producer.application.port.out.PublishOrderProposalPort;
import com.example.producer.domain.OrderProposalProposed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.example.producer.adapter.out.messaging.OrderProposedV1Mapper.from;

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
        var contract = from(event);
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

}
