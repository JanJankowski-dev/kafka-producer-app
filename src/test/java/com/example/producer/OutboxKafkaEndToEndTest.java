package com.example.producer;

import com.example.producer.adapter.out.messaging.OrderProposedV1;
import com.example.producer.application.service.OutboxRelayService;
import com.example.producer.configuration.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static com.example.producer.jooq.tables.IdempotencyRequests.IDEMPOTENCY_REQUESTS;
import static com.example.producer.jooq.tables.OrderProposalEvidenceSources.ORDER_PROPOSAL_EVIDENCE_SOURCES;
import static com.example.producer.jooq.tables.OrderProposals.ORDER_PROPOSALS;
import static com.example.producer.jooq.tables.OutboxEvents.OUTBOX_EVENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@EmbeddedKafka(
        partitions = 1,
        topics = OutboxKafkaEndToEndTest.TOPIC,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
@TestPropertySource(properties = "app.kafka.topic=investment.order-proposals.e2e.v1")
class OutboxKafkaEndToEndTest {
    static final String TOPIC = "investment.order-proposals.e2e.v1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxRelayService relay;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker kafka;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(IDEMPOTENCY_REQUESTS).execute();
        dsl.deleteFrom(ORDER_PROPOSAL_EVIDENCE_SOURCES).execute();
        dsl.deleteFrom(OUTBOX_EVENTS).execute();
        dsl.deleteFrom(ORDER_PROPOSALS).execute();
    }

    @Test
    void shouldAcceptHttpRequestAndPublishStoredEventToKafka() throws Exception {
        var httpResult = mockMvc.perform(post("/order-proposals")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isAccepted())
                .andReturn();
        var response = objectMapper.readTree(httpResult.getResponse().getContentAsString());
        UUID eventId = UUID.fromString(response.required("eventId").asText());

        int processed = relay.relayBatch();

        assertThat(processed).isOne();
        var consumerProperties = KafkaTestUtils.consumerProps(
                "outbox-e2e-" + UUID.randomUUID(),
                "true",
                kafka
        );
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var consumerFactory = new DefaultKafkaConsumerFactory<>(
                consumerProperties,
                new StringDeserializer(),
                new StringDeserializer()
        );
        try (var consumer = consumerFactory.createConsumer()) {
            kafka.consumeFromAnEmbeddedTopic(consumer, TOPIC);
            var record = KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(10));
            var event = objectMapper.readValue(record.value(), OrderProposedV1.class);

            assertThat(record.key()).isEqualTo("256f29f4-24d3-4737-88e1-6414a93a3fdc");
            assertThat(event.eventId()).isEqualTo(eventId);
            assertThat(event.portfolioId().toString()).isEqualTo(record.key());
        }

        var outbox = dsl.selectFrom(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.EVENT_ID.eq(eventId))
                .fetchSingle();
        assertThat(outbox.getStatus()).isEqualTo("PUBLISHED");
        assertThat(outbox.getAttempts()).isOne();
    }

    private String validJson() {
        return """
                {
                  "portfolioId": "256f29f4-24d3-4737-88e1-6414a93a3fdc",
                  "instrumentId": "16377765-cc93-4147-a59c-8e8ac634a073",
                  "side": "BUY",
                  "maxNotional": 123.45,
                  "currency": "PLN",
                  "source": "AI_LAB",
                  "rationale": "Positive earnings revision",
                  "evidenceSources": ["https://example.com/report"],
                  "confidence": 0.72,
                  "validUntil": "2099-09-13T10:00:00Z"
                }
                """;
    }
}
