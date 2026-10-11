package com.example.producer.adapter.out;

import com.example.producer.application.port.out.HistoricalPricesPort;
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
public class EodhdHistoricalPricesAdapter implements HistoricalPricesPort {
    private final String token;
    private final ObjectMapper mapper;
    private final HttpClient client;

    @Autowired
    public EodhdHistoricalPricesAdapter(@Value("${app.market-data.eodhd-token:}") String token,
                                        ObjectMapper mapper) {
        this(token, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    EodhdHistoricalPricesAdapter(String token, ObjectMapper mapper, HttpClient client) {
        this.token = token.trim();
        this.mapper = mapper;
        this.client = client;
    }

    @Override
    public boolean configured() {
        return !token.isBlank();
    }

    @Override
    public List<DailyPrice> fetch(String symbol, LocalDate startDate, LocalDate endDate) {
        if (!configured()) throw new MarketDataUnavailableException("EODHD API key is not configured");
        if (!symbol.matches("[A-Z]{1,8}")) throw new IllegalArgumentException("Unsupported market symbol");
        URI uri = URI.create("https://eodhd.com/api/eod/" + symbol + ".US?api_token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&fmt=json&from=" + startDate + "&to=" + endDate + "&period=d");
        HttpRequest request = HttpRequest.newBuilder(uri).header("Accept", "application/json")
                .timeout(Duration.ofSeconds(25)).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new MarketDataUnavailableException("EODHD returned HTTP " + response.statusCode()
                        + " for " + symbol);
            }
            JsonNode body = mapper.readTree(response.body());
            if (!body.isArray()) throw new MarketDataUnavailableException("EODHD returned no price series for " + symbol);
            var prices = new ArrayList<DailyPrice>();
            for (JsonNode row : body) {
                try {
                    prices.add(new DailyPrice(LocalDate.parse(row.path("date").asText()),
                            new BigDecimal(row.path("open").asText()),
                            new BigDecimal(row.path("close").asText()),
                            new BigDecimal(row.path("adjusted_close").asText()),
                            row.path("volume").asLong(-1), BigDecimal.ONE, BigDecimal.ZERO));
                } catch (RuntimeException exception) {
                    throw new MarketDataUnavailableException("Invalid EODHD price row for " + symbol, exception);
                }
            }
            prices.sort(Comparator.comparing(DailyPrice::sessionDate));
            return List.copyOf(prices);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MarketDataUnavailableException("EODHD request was interrupted", exception);
        } catch (IOException exception) {
            throw new MarketDataUnavailableException("Could not read EODHD prices for " + symbol, exception);
        }
    }
}
