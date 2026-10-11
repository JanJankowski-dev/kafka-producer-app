package com.example.producer.adapter.in.web;

import com.example.producer.application.service.PaperSimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@Profile("synthetic-demo")
@RequestMapping("/paper-simulation")
public class PaperSimulationController {
    private final PaperSimulationService simulation;

    public PaperSimulationController(PaperSimulationService simulation) {
        this.simulation = simulation;
    }

    @GetMapping("/sessions")
    public List<LocalDate> sessions() {
        return simulation.availableSessions();
    }

    @PostMapping("/portfolios/{portfolioId}/sessions/{session}")
    public ResponseEntity<?> advance(@PathVariable UUID portfolioId, @PathVariable LocalDate session) {
        try {
            var result = simulation.advance(portfolioId, session);
            return ResponseEntity.ok(new SessionResponse(result.runId(),
                    AvailableMarketScreeningResponse.from(result.screening()), result.settledOrders()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiErrorResponse("INVALID_SESSION", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(new ApiErrorResponse("SESSION_CONFLICT", exception.getMessage()));
        }
    }

    @PostMapping("/portfolios/{portfolioId}/orders")
    public ResponseEntity<?> order(@PathVariable UUID portfolioId, @RequestBody OrderRequest request) {
        try {
            return ResponseEntity.accepted().body(simulation.submit(portfolioId,
                    request.clientOrderId(), request.runId(), request.instrumentId(),
                    request.side(), request.quantity()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ApiErrorResponse("INVALID_PAPER_ORDER", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(409).body(new ApiErrorResponse("PAPER_ORDER_CONFLICT", exception.getMessage()));
        }
    }

    @GetMapping("/portfolios/{portfolioId}/orders")
    public ResponseEntity<?> orders(@PathVariable UUID portfolioId) {
        try {
            return ResponseEntity.ok(simulation.orders(portfolioId));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    public record OrderRequest(UUID clientOrderId, UUID runId, UUID instrumentId,
                               String side, BigDecimal quantity) {
    }

    public record SessionResponse(UUID runId, AvailableMarketScreeningResponse screening,
                                  List<com.example.producer.application.port.out.PaperSessionStorePort.OrderView> settledOrders) {
    }
}
