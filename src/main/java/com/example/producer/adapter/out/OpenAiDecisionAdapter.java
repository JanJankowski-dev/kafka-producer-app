package com.example.producer.adapter.out;

import com.example.producer.application.port.out.AiDecisionPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OpenAiDecisionAdapter implements AiDecisionPort {
    private final String apiKey;
    private final String model;
    private final boolean externalMarketDataSharingAllowed;
    private final ObjectMapper mapper;
    private final HttpClient client;

    @Autowired
    public OpenAiDecisionAdapter(@Value("${app.ai.api-key:}") String apiKey,
                                 @Value("${app.ai.model:gpt-5.4-mini}") String model,
                                 @Value("${app.ai.external-market-data-sharing-allowed:false}") boolean externalMarketDataSharingAllowed,
                                 ObjectMapper mapper) {
        this(apiKey, model, externalMarketDataSharingAllowed, mapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    OpenAiDecisionAdapter(String apiKey, String model, ObjectMapper mapper) {
        this(apiKey, model, true, mapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    OpenAiDecisionAdapter(String apiKey, String model, ObjectMapper mapper, HttpClient client) {
        this(apiKey, model, true, mapper, client);
    }

    OpenAiDecisionAdapter(String apiKey, String model, boolean externalMarketDataSharingAllowed,
                          ObjectMapper mapper, HttpClient client) {
        this.apiKey = apiKey.trim();
        this.model = model.trim();
        this.externalMarketDataSharingAllowed = externalMarketDataSharingAllowed;
        this.mapper = mapper;
        this.client = client;
    }

    @Override public String modelVersion() {
        return !externalMarketDataSharingAllowed ? "DATA_SHARING_DISABLED" : configured() ? model : "UNCONFIGURED";
    }
    @Override public boolean configured() {
        return externalMarketDataSharingAllowed && !apiKey.isBlank() && !model.isBlank();
    }

    @Override
    public List<Decision> decide(LocalDate session, List<Candidate> candidates) {
        if (!configured()) throw new IllegalStateException("AI provider is not configured");
        try {
            var schema = Map.of(
                    "type", "object", "additionalProperties", false,
                    "required", List.of("decisions"),
                    "properties", Map.of("decisions", Map.of(
                            "type", "array",
                            "items", Map.of("type", "object", "additionalProperties", false,
                                    "required", List.of("symbol", "action", "reason"),
                                    "properties", Map.of(
                                            "symbol", Map.of("type", "string"),
                                            "action", Map.of("type", "string", "enum", List.of("BUY", "SELL", "HOLD")),
                                            "reason", Map.of("type", "string"))))));
            var body = Map.of(
                    "model", model,
                    "store", false,
                    "max_output_tokens", 1200,
                    "instructions", "You are a PAPER trading research component, not a broker. "
                            + "Return exactly one decision per supplied symbol. Use only supplied as-of-session "
                            + "numbers; no outside or future data. SELL only if held; BUY only if not held. "
                            + "When evidence is weak, HOLD. Reasons must be concise and factual.",
                    "input", mapper.writeValueAsString(Map.of("session", session.toString(),
                            "candidates", candidates)),
                    "text", Map.of("format", Map.of("type", "json_schema", "name", "paper_decisions",
                            "strict", true, "schema", schema)));
            var request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(45))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("AI provider returned HTTP " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            if (!"completed".equals(root.path("status").asText())) {
                throw new IllegalStateException("AI response was not completed");
            }
            String output = null;
            for (JsonNode item : root.path("output")) {
                for (JsonNode content : item.path("content")) {
                    if ("output_text".equals(content.path("type").asText())) {
                        output = content.path("text").asText();
                    }
                }
            }
            if (output == null) throw new IllegalStateException("AI response contained no structured text");
            JsonNode decisions = mapper.readTree(output).path("decisions");
            if (!decisions.isArray() || decisions.size() != candidates.size()) {
                throw new IllegalStateException("AI returned an incomplete decision set");
            }
            Set<String> allowed = new HashSet<>();
            candidates.forEach(candidate -> allowed.add(candidate.symbol()));
            Set<String> seen = new HashSet<>();
            var parsed = new ArrayList<Decision>();
            for (JsonNode item : decisions) {
                String symbol = item.path("symbol").asText();
                String action = item.path("action").asText();
                String reason = item.path("reason").asText();
                if (!allowed.contains(symbol) || !seen.add(symbol)
                        || !Set.of("BUY", "SELL", "HOLD").contains(action)
                        || reason.isBlank() || reason.length() > 400) {
                    throw new IllegalStateException("AI returned an invalid decision");
                }
                parsed.add(new Decision(symbol, action, reason));
            }
            return List.copyOf(parsed);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI request interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("AI request failed", exception);
        }
    }
}
