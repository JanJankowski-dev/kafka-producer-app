package com.example.producer.adapter.in.web;

import com.example.producer.application.port.in.OrderProposalAccepted;
import com.example.producer.application.port.in.ProposeOrderCommand;
import com.example.producer.application.port.in.ProposeOrderUseCase;
import com.example.producer.domain.OrderSide;
import com.example.producer.domain.ProposalSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class OrderProposalsControllerTest {
    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private ProposeOrderUseCase useCase;

    @Test
    void shouldReturnAcceptedResponse() throws Exception {
        var eventId = UUID.fromString("269ad5c6-69ea-4f88-8f21-aa4e283db26e");
        var proposalId = UUID.fromString("59bd5b26-0c71-4bac-aad4-80e73c9bd9a8");
        when(useCase.propose(any(ProposeOrderCommand.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        new OrderProposalAccepted(eventId, proposalId)
                ));

        MvcResult pending = mockMvc().perform(validRequest())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc().perform(asyncDispatch(pending))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.proposalId").value(proposalId.toString()));
    }

    @Test
    void shouldReturnBadRequestForNegativeNotional() throws Exception {
        mockMvc().perform(MockMvcRequestBuilders.post("/order-proposals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("123.45", "-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestForUnknownCurrency() throws Exception {
        MvcResult pending = mockMvc().perform(MockMvcRequestBuilders.post("/order-proposals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("PLN", "ZZZ")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc().perform(asyncDispatch(pending))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_PROPOSAL"));
    }

    @Test
    void shouldReturnServiceUnavailableWhenPublishingFails() throws Exception {
        when(useCase.propose(any(ProposeOrderCommand.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("Kafka unavailable")
                ));

        MvcResult pending = mockMvc().perform(validRequest())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc().perform(asyncDispatch(pending))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ORDER_PROPOSAL_UNAVAILABLE"));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private MockHttpServletRequestBuilder validRequest() {
        return MockMvcRequestBuilders.post("/order-proposals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validJson());
    }

    private String validJson() {
        return """
                {
                  "portfolioId": "256f29f4-24d3-4737-88e1-6414a93a3fdc",
                  "instrumentId": "16377765-cc93-4147-a59c-8e8ac634a073",
                  "side": "%s",
                  "maxNotional": 123.45,
                  "currency": "PLN",
                  "strategySource": "%s",
                  "rationale": "Positive earnings revision",
                  "evidenceSources": ["https://example.com/report"],
                  "confidence": 0.72,
                  "validUntil": "2099-09-13T10:00:00Z"
                }
                """.formatted(OrderSide.BUY, ProposalSource.AI_LAB);
    }
}
