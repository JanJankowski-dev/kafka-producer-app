package com.example.producer.adapter.out;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiDecisionAdapterTest {
    @Test
    void dataSharingGatePreventsExternalAiCallEvenWithApiKey() {
        HttpClient client = mock(HttpClient.class);
        var adapter = new OpenAiDecisionAdapter("test-key", "gpt-5.4-mini", false,
                new ObjectMapper(), client);
        assertThat(adapter.configured()).isFalse();
        assertThat(adapter.modelVersion()).isEqualTo("DATA_SHARING_DISABLED");
        assertThatThrownBy(() -> adapter.decide(LocalDate.of(2026, 10, 9), List.of()))
                .isInstanceOf(IllegalStateException.class);
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    void missingKeyFailsClosedWithoutCallingProvider() {
        var adapter = new OpenAiDecisionAdapter("", "gpt-5.4-mini", new ObjectMapper());
        assertThat(adapter.configured()).isFalse();
        assertThat(adapter.modelVersion()).isEqualTo("UNCONFIGURED");
        assertThatThrownBy(() -> adapter.decide(LocalDate.of(2026, 10, 2), List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptsOnlyCompletedStructuredDecisionSet() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"status\":\"completed\",\"output\":[{\"content\":["
                + "{\"type\":\"output_text\",\"text\":\"{\\\"decisions\\\":[{\\\"symbol\\\":\\\"NVDA\\\","
                + "\\\"action\\\":\\\"BUY\\\",\\\"reason\\\":\\\"test\\\"}]}\"}]}]}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        var adapter = new OpenAiDecisionAdapter("test-key", "gpt-5.4-mini",
                new ObjectMapper(), client);
        var candidates = List.of(new com.example.producer.application.port.out.AiDecisionPort.Candidate(
                "NVDA", new BigDecimal("100"), new BigDecimal("2"), false, 0));
        assertThat(adapter.decide(LocalDate.of(2026, 10, 2), candidates))
                .containsExactly(new com.example.producer.application.port.out.AiDecisionPort.Decision(
                        "NVDA", "BUY", "test"));

        when(response.body()).thenReturn("{\"status\":\"incomplete\",\"output\":[]}");
        assertThatThrownBy(() -> adapter.decide(LocalDate.of(2026, 10, 2), candidates))
                .isInstanceOf(IllegalStateException.class);
    }
}
