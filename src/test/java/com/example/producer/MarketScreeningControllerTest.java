package com.example.producer;

import com.example.producer.adapter.in.web.MarketScreeningRequest;
import com.example.producer.domain.strategy.AssetType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.liquibase.enabled=false",
        "app.outbox.enabled=false",
        "app.market-data.local-mode=true"
})
@ActiveProfiles("synthetic-demo")
class MarketScreeningControllerTest {
    private static final LocalDate FIRST_SESSION = LocalDate.of(2026, 9, 1);
    private static final LocalDate AS_OF = FIRST_SESSION.plusDays(20);

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void previewsAStockBuyAndAnEtfSellWithoutCreatingOrders() throws Exception {
        var request = new MarketScreeningRequest(AS_OF, List.of(
                new MarketScreeningRequest.InstrumentRequest(UUID.randomUUID(),
                        AssetType.STOCK, closes(1), null),
                new MarketScreeningRequest.InstrumentRequest(UUID.randomUUID(),
                        AssetType.ETF, closes(2), 20)
        ));

        mockMvc().perform(post("/market-screenings/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.researchOnly").value(true))
                .andExpect(jsonPath("$.signals[0].assetType").value("ETF"))
                .andExpect(jsonPath("$.signals[0].action").value("SELL"))
                .andExpect(jsonPath("$.signals[0].reasonCode").value("MAX_HOLDING_PERIOD"))
                .andExpect(jsonPath("$.signals[1].assetType").value("STOCK"))
                .andExpect(jsonPath("$.signals[1].action").value("BUY"))
                .andExpect(jsonPath("$.signals[1].maxHoldingSessions").value(20));
    }

    @Test
    void demoScreensSyntheticStocksAndEtfsFromBothMarkets() throws Exception {
        mockMvc().perform(get("/market-screenings/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synthetic").value(true))
                .andExpect(jsonPath("$.researchOnly").value(true))
                .andExpect(jsonPath("$.asOfSession").value("2026-10-02"))
                .andExpect(jsonPath("$.instruments.length()").value(4))
                .andExpect(jsonPath("$.instruments[0].market").value("US"))
                .andExpect(jsonPath("$.instruments[0].signal.action").value("SELL"))
                .andExpect(jsonPath("$.instruments[1].market").value("US"))
                .andExpect(jsonPath("$.instruments[1].signal.action").value("BUY"))
                .andExpect(jsonPath("$.instruments[2].market").value("GPW"))
                .andExpect(jsonPath("$.instruments[2].signal.action").value("BUY"))
                .andExpect(jsonPath("$.instruments[3].market").value("GPW"))
                .andExpect(jsonPath("$.instruments[3].signal.action").value("NO_ACTION"));
    }

    @Test
    void servesTheLocalDemoPage() throws Exception {
        mockMvc().perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void rejectsDataFromAfterTheRequestedSession() throws Exception {
        var request = new MarketScreeningRequest(AS_OF.minusDays(1), List.of(
                new MarketScreeningRequest.InstrumentRequest(UUID.randomUUID(),
                        AssetType.STOCK, closes(1), null)
        ));

        mockMvc().perform(post("/market-screenings/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MARKET_SCREENING"));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private List<MarketScreeningRequest.CloseRequest> closes(int increment) {
        var closes = new ArrayList<MarketScreeningRequest.CloseRequest>();
        for (int index = 0; index <= 20; index++) {
            closes.add(new MarketScreeningRequest.CloseRequest(
                    FIRST_SESSION.plusDays(index),
                    BigDecimal.valueOf(100L + (long) index * increment)
            ));
        }
        return closes;
    }
}
