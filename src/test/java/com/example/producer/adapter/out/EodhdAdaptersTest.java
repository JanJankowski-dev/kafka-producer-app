package com.example.producer.adapter.out;

import com.example.producer.application.port.out.EtfNavSourcePort;
import com.example.producer.application.service.MarketDataUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EodhdAdaptersTest {
    @Test
    @SuppressWarnings("unchecked")
    void stockAdapterReadsEodhdJsonWithoutInventingCorporateActions() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("[{\"date\":\"2026-10-09\",\"open\":100.25,"
                + "\"close\":101.50,\"adjusted_close\":100.75,\"volume\":12345}]");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var adapter = new EodhdHistoricalPricesAdapter("test-key", new ObjectMapper(), client);

        var prices = adapter.fetch("AAPL", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9));

        assertThat(prices).hasSize(1);
        assertThat(prices.get(0).rawOpen()).isEqualByComparingTo("100.25");
        assertThat(prices.get(0).adjustedClose()).isEqualByComparingTo("100.75");
        assertThat(prices.get(0).splitFactor()).isEqualByComparingTo("1");
        assertThat(prices.get(0).dividendCash()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> adapter.fetch("AAPL.US", LocalDate.now(), LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void etfAdapterUsesUsdSwissListingAndRawMarketClose() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("[{\"date\":\"2026-10-09\",\"close\":841.10}]");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var adapter = new EodhdEtfPricesAdapter("test-key", new ObjectMapper(), client);

        assertThat(EtfNavSourcePort.Fund.CSPX.eodhdTicker()).isEqualTo("CSSPX.SW");
        assertThat(EtfNavSourcePort.Fund.IWDA.eodhdTicker()).isEqualTo("SWDA.SW");
        assertThat(adapter.download(EtfNavSourcePort.Fund.CSPX).get(0).navUsd())
                .isEqualByComparingTo("841.10");
        when(response.body()).thenReturn("[{\"date\":\"2026-10-09\",\"close\":0}]");
        assertThatThrownBy(() -> adapter.download(EtfNavSourcePort.Fund.CSPX))
                .isInstanceOf(MarketDataUnavailableException.class);
    }
}
