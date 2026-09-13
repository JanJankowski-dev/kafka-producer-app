package com.example.producer.adapter.out.kafka;

import com.example.producer.domain.Confidence;
import com.example.producer.domain.InstrumentId;
import com.example.producer.domain.NotionalLimit;
import com.example.producer.domain.OrderProposal;
import com.example.producer.domain.OrderProposalProposed;
import com.example.producer.domain.OrderSide;
import com.example.producer.domain.PortfolioId;
import com.example.producer.domain.ProposalSource;
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
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class KafkaOrderProposalPublisherTest {
    @Mock
    private KafkaTemplate<String, OrderProposedV1> kafkaTemplate;

    @Test
    void shouldMapAndPublishEventUsingPortfolioIdAsKey() {
        var event = event();
        var contractCaptor = ArgumentCaptor.forClass(OrderProposedV1.class);
        var sendResult = sendResult();
        when(kafkaTemplate.send(
                eq("investment.order-proposals.v1"),
                eq(event.proposal().portfolioId().value().toString()),
                contractCaptor.capture()
        )).thenReturn(CompletableFuture.completedFuture(sendResult));
        var publisher = new KafkaOrderProposalPublisher(
                kafkaTemplate,
                "investment.order-proposals.v1"
        );

        publisher.publish(event).toCompletableFuture().join();

        verify(kafkaTemplate).send(
                "investment.order-proposals.v1",
                event.proposal().portfolioId().value().toString(),
                contractCaptor.getValue()
        );
        var contract = contractCaptor.getValue();
        assertThat(contract.eventId()).isEqualTo(event.eventId());
        assertThat(contract.proposalId()).isEqualTo(event.proposal().proposalId());
        assertThat(contract.side()).isEqualTo("BUY");
        assertThat(contract.strategySource()).isEqualTo("AI_LAB");
        assertThat(contract.maxNotional()).isEqualByComparingTo("123.45");
        assertThat(contract.currency()).isEqualTo("PLN");
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

    private OrderProposalProposed event() {
        var proposal = new OrderProposal(
                UUID.fromString("59bd5b26-0c71-4bac-aad4-80e73c9bd9a8"),
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
        return new OrderProposalProposed(
                UUID.fromString("269ad5c6-69ea-4f88-8f21-aa4e283db26e"),
                Instant.parse("2026-09-12T10:00:00Z"),
                1,
                proposal
        );
    }
}
