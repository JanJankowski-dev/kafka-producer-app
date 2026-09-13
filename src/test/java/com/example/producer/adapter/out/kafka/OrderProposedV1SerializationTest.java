package com.example.producer.adapter.out.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderProposedV1SerializationTest {
    @Test
    void shouldPreserveBigDecimalPrecisionWithoutJavaTypeHeader() {
        var event = event();

        try (var serializer = new JsonSerializer<OrderProposedV1>();
             var deserializer = new JsonDeserializer<>(OrderProposedV1.class, false)) {
            serializer.setAddTypeInfo(false);

            byte[] payload = serializer.serialize("investment.order-proposals.v1", event);
            var deserialized = deserializer.deserialize("investment.order-proposals.v1", payload);
            var json = new String(payload, StandardCharsets.UTF_8);

            assertThat(json).contains(
                    "\"maxNotional\":12345678901234567890.123456789",
                    "\"confidence\":0.72",
                    "\"strategySource\":\"AI_LAB\"",
                    "\"evidenceSources\":[\"https://example.com/report\"]"
            );
            assertThat(deserialized.maxNotional()).isEqualByComparingTo(event.maxNotional());
            assertThat(deserialized).isEqualTo(event);
        }
    }

    private OrderProposedV1 event() {
        return new OrderProposedV1(
                UUID.fromString("269ad5c6-69ea-4f88-8f21-aa4e283db26e"),
                UUID.fromString("59bd5b26-0c71-4bac-aad4-80e73c9bd9a8"),
                Instant.parse("2026-09-12T10:00:00Z"),
                1,
                UUID.fromString("256f29f4-24d3-4737-88e1-6414a93a3fdc"),
                UUID.fromString("16377765-cc93-4147-a59c-8e8ac634a073"),
                "BUY",
                new BigDecimal("12345678901234567890.123456789"),
                "PLN",
                "AI_LAB",
                "Positive earnings revision",
                List.of("https://example.com/report"),
                new BigDecimal("0.72"),
                Instant.parse("2026-09-13T10:00:00Z")
        );
    }
}
