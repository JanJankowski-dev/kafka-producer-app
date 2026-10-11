package com.example.producer.adapter.out.kafka;

import com.example.producer.adapter.out.messaging.OrderProposedV1;
import com.example.producer.application.port.out.PendingOutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxEventPublisherTest {
    private static final String TOPIC = "investment.order-proposals.v1";
    private static final UUID EVENT_ID = UUID.fromString("269ad5c6-69ea-4f88-8f21-aa4e283db26e");
    private static final UUID PORTFOLIO_ID = UUID.fromString("256f29f4-24d3-4737-88e1-6414a93a3fdc");

    @Mock
    private KafkaTemplate<String, OrderProposedV1> kafkaTemplate;

    @Test
    void shouldPublishStoredContractUsingOutboxPartitionKey() throws Exception {
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        var contract = contract();
        var event = new PendingOutboxEvent(
                EVENT_ID,
                UUID.randomUUID(),
                PORTFOLIO_ID.toString(),
                "OrderProposedV1",
                1,
                objectMapper.writeValueAsString(contract),
                1
        );
        var contractCaptor = ArgumentCaptor.forClass(OrderProposedV1.class);
        var result = sendResult();
        when(kafkaTemplate.send(eq(TOPIC), eq(PORTFOLIO_ID.toString()), contractCaptor.capture()))
                .thenReturn(CompletableFuture.completedFuture(result));
        var publisher = new KafkaOutboxEventPublisher(kafkaTemplate, objectMapper, TOPIC);

        publisher.publish(event).toCompletableFuture().join();

        verify(kafkaTemplate).send(TOPIC, PORTFOLIO_ID.toString(), contractCaptor.getValue());
        assertThat(contractCaptor.getValue()).isEqualTo(contract);
    }

    @Test
    void shouldRejectPayloadWhoseEventIdDoesNotMatchOutboxMetadata() throws Exception {
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        var event = new PendingOutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                PORTFOLIO_ID.toString(),
                "OrderProposedV1",
                1,
                objectMapper.writeValueAsString(contract()),
                1
        );
        var publisher = new KafkaOutboxEventPublisher(kafkaTemplate, objectMapper, TOPIC);

        assertThatThrownBy(() -> publisher.publish(event).toCompletableFuture().join())
                .hasRootCauseMessage("Outbox eventId does not match payload");
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, OrderProposedV1> sendResult() {
        var result = (SendResult<String, OrderProposedV1>) mock(SendResult.class);
        var metadata = mock(RecordMetadata.class);
        when(metadata.partition()).thenReturn(2);
        when(metadata.offset()).thenReturn(41L);
        when(result.getRecordMetadata()).thenReturn(metadata);
        return result;
    }

    private OrderProposedV1 contract() {
        return new OrderProposedV1(
                EVENT_ID,
                UUID.fromString("59bd5b26-0c71-4bac-aad4-80e73c9bd9a8"),
                Instant.parse("2026-09-12T10:00:00Z"),
                1,
                PORTFOLIO_ID,
                UUID.fromString("16377765-cc93-4147-a59c-8e8ac634a073"),
                "BUY",
                new BigDecimal("123.45"),
                "PLN",
                "AI_LAB",
                "Positive earnings revision",
                List.of("https://example.com/report"),
                new BigDecimal("0.72"),
                Instant.parse("2026-09-13T10:00:00Z")
        );
    }
}
