package com.example.producer;

import com.example.producer.application.port.in.ProposeOrderCommand;
import com.example.producer.application.port.in.ProposeOrderUseCase;
import com.example.producer.configuration.PostgresIntegrationTest;
import com.example.producer.domain.Confidence;
import com.example.producer.domain.InstrumentId;
import com.example.producer.domain.NotionalLimit;
import com.example.producer.domain.OrderSide;
import com.example.producer.domain.PortfolioId;
import com.example.producer.domain.ProposalSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.example.producer.jooq.tables.IdempotencyRequests.IDEMPOTENCY_REQUESTS;
import static com.example.producer.jooq.tables.OrderProposalEvidenceSources.ORDER_PROPOSAL_EVIDENCE_SOURCES;
import static com.example.producer.jooq.tables.OrderProposals.ORDER_PROPOSALS;
import static com.example.producer.jooq.tables.OutboxEvents.OUTBOX_EVENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@PostgresIntegrationTest
@AutoConfigureMockMvc
class IdempotencyHttpIntegrationTest {
    private static final Instant FIRST_ATTEMPT = Instant.parse("2026-09-12T10:00:00Z");
    private static final Instant EXPIRED = Instant.parse("2026-09-14T10:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private ProposeOrderUseCase useCase;
    @MockitoBean private Clock clock;

    @BeforeEach
    void cleanDatabase() {
        when(clock.instant()).thenReturn(FIRST_ATTEMPT);
        dsl.deleteFrom(IDEMPOTENCY_REQUESTS).execute();
        dsl.deleteFrom(ORDER_PROPOSAL_EVIDENCE_SOURCES).execute();
        dsl.deleteFrom(OUTBOX_EVENTS).execute();
        dsl.deleteFrom(ORDER_PROPOSALS).execute();
    }

    @Test
    void retryAfterValidityExpiresReplaysTheOriginalResponse() throws Exception {
        String key = UUID.randomUUID().toString();
        var first = mockMvc.perform(request(key, validJson()))
                .andExpect(status().isAccepted())
                .andReturn();

        when(clock.instant()).thenReturn(EXPIRED);
        var retry = mockMvc.perform(request(key, validJson()))
                .andExpect(status().isAccepted())
                .andReturn();

        assertThat(objectMapper.readTree(retry.getResponse().getContentAsString()))
                .isEqualTo(objectMapper.readTree(first.getResponse().getContentAsString()));
        assertThat(dsl.fetchCount(ORDER_PROPOSALS)).isOne();
        assertThat(dsl.fetchCount(OUTBOX_EVENTS)).isOne();
        assertThat(dsl.fetchCount(IDEMPOTENCY_REQUESTS)).isOne();

        mockMvc.perform(request(UUID.randomUUID().toString(), validJson()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sameKeyWithDifferentPayloadReturnsConflict() throws Exception {
        String key = UUID.randomUUID().toString();
        mockMvc.perform(request(key, validJson()))
                .andExpect(status().isAccepted());

        mockMvc.perform(request(key, validJson().replace("123.45", "234.56")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));

        assertThat(dsl.fetchCount(ORDER_PROPOSALS)).isOne();
        assertThat(dsl.fetchCount(OUTBOX_EVENTS)).isOne();
    }

    @Test
    void differentKeysForTheSamePayloadCreateDistinctProposals() throws Exception {
        var first = mockMvc.perform(request(UUID.randomUUID().toString(), validJson()))
                .andExpect(status().isAccepted())
                .andReturn();
        var second = mockMvc.perform(request(UUID.randomUUID().toString(), validJson()))
                .andExpect(status().isAccepted())
                .andReturn();

        assertThat(objectMapper.readTree(first.getResponse().getContentAsString()))
                .isNotEqualTo(objectMapper.readTree(second.getResponse().getContentAsString()));
        assertThat(dsl.fetchCount(ORDER_PROPOSALS)).isEqualTo(2);
        assertThat(dsl.fetchCount(OUTBOX_EVENTS)).isEqualTo(2);
    }

    @Test
    void rejectedNewRequestDoesNotReserveItsKey() throws Exception {
        String key = UUID.randomUUID().toString();
        mockMvc.perform(request(key, validJson().replace(
                        "2026-09-13T10:00:00Z", "2026-09-12T09:00:00Z")))
                .andExpect(status().isBadRequest());

        assertThat(dsl.fetchCount(IDEMPOTENCY_REQUESTS)).isZero();
        mockMvc.perform(request(key, validJson()))
                .andExpect(status().isAccepted());
        assertThat(dsl.fetchCount(IDEMPOTENCY_REQUESTS)).isOne();
    }

    @Test
    void equivalentDecimalRepresentationsAreTheSameRequest() throws Exception {
        String key = UUID.randomUUID().toString();
        var first = mockMvc.perform(request(key, validJson()))
                .andExpect(status().isAccepted())
                .andReturn();
        var retry = mockMvc.perform(request(key, validJson().replace("123.45", "123.450")))
                .andExpect(status().isAccepted())
                .andReturn();

        assertThat(objectMapper.readTree(retry.getResponse().getContentAsString()))
                .isEqualTo(objectMapper.readTree(first.getResponse().getContentAsString()));
        assertThat(dsl.fetchCount(ORDER_PROPOSALS)).isOne();
    }

    @Test
    void twoConcurrentRequestsCreateOneProposalAndOneOutboxEvent() throws Exception {
        String key = UUID.randomUUID().toString();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return useCase.propose(key, command());
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return useCase.propose(key, command());
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS))
                    .isEqualTo(second.get(10, TimeUnit.SECONDS));
            assertThat(dsl.fetchCount(ORDER_PROPOSALS)).isOne();
            assertThat(dsl.fetchCount(OUTBOX_EVENTS)).isOne();
            assertThat(dsl.fetchCount(IDEMPOTENCY_REQUESTS)).isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String key,
            String json
    ) {
        return post("/order-proposals")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private ProposeOrderCommand command() {
        return new ProposeOrderCommand(
                new PortfolioId(UUID.fromString("256f29f4-24d3-4737-88e1-6414a93a3fdc")),
                new InstrumentId(UUID.fromString("16377765-cc93-4147-a59c-8e8ac634a073")),
                OrderSide.BUY,
                new NotionalLimit(new BigDecimal("123.45"), Currency.getInstance("PLN")),
                ProposalSource.AI_LAB,
                "Positive earnings revision",
                List.of("https://example.com/report"),
                new Confidence(new BigDecimal("0.72")),
                Instant.parse("2026-09-13T10:00:00Z")
        );
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
                  "validUntil": "2026-09-13T10:00:00Z"
                }
                """;
    }
}
