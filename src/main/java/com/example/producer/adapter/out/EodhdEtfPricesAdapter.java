package com.example.producer.adapter.out;

import com.example.producer.application.port.out.EtfNavSourcePort;
import com.example.producer.application.service.MarketDataUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class EodhdEtfPricesAdapter implements EtfNavSourcePort {
    private final String token;
    private final ObjectMapper mapper;
    private final HttpClient client;

    @Autowired
    public EodhdEtfPricesAdapter(@Value("${app.market-data.eodhd-token:}") String token,
                                 ObjectMapper mapper) {
        this(token, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    EodhdEtfPricesAdapter(String token, ObjectMapper mapper, HttpClient client) {
        this.token = token.trim();
        this.mapper = mapper;
        this.client = client;
    }

    @Override
    public List<Nav> download(Fund fund) {
        if (token.isBlank()) throw new MarketDataUnavailableException("EODHD API key is not configured");
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        URI uri = URI.create("https://eodhd.com/api/eod/" + fund.eodhdTicker() + "?api_token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&fmt=json&from=" + today.minusDays(45) + "&to=" + today + "&period=d");
        HttpRequest request = HttpRequest.newBuilder(uri).header("Accept", "application/json")
                .timeout(Duration.ofSeconds(25)).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new MarketDataUnavailableException("EODHD returned HTTP " + response.statusCode()
                        + " for " + fund.name());
            }
            JsonNode body = mapper.readTree(response.body());
            if (!body.isArray()) throw new MarketDataUnavailableException("EODHD returned no ETF prices");
            var prices = new ArrayList<Nav>();
            for (JsonNode row : body) {
                try {
                    BigDecimal close = new BigDecimal(row.path("close").asText());
                    if (close.signum() <= 0) throw new IllegalArgumentException("Non-positive ETF close");
                    prices.add(new Nav(LocalDate.parse(row.path("date").asText()), close));
                } catch (RuntimeException exception) {
                    throw new MarketDataUnavailableException("Invalid EODHD ETF price row for " + fund.name(), exception);
                }
            }
            prices.sort(Comparator.comparing(Nav::session));
            return List.copyOf(prices);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MarketDataUnavailableException("EODHD ETF request was interrupted", exception);
        } catch (IOException exception) {
            throw new MarketDataUnavailableException("Could not read EODHD ETF prices", exception);
        }
    }
}
