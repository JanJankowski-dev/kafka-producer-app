package com.example.producer.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PaperSessionStorePort {
    LocalDate lockLastSession(UUID portfolioId);
    void setLastSession(UUID portfolioId, LocalDate session);
    List<HeldPosition> positions(UUID portfolioId);
    List<OrderView> pendingOrders(UUID portfolioId);
    List<OrderView> orders(UUID portfolioId);
    OrderView findOrder(UUID portfolioId, UUID clientOrderId);
    RunSignal runSignal(UUID portfolioId, UUID runId, UUID instrumentId);
    OrderView insertPending(UUID portfolioId, UUID clientOrderId, UUID runId,
                            UUID instrumentId, String side, BigDecimal quantity,
                            LocalDate requestedSession);
    void markFilled(UUID orderId, UUID tradeId);
    void markRejected(UUID orderId, String reason);

    record HeldPosition(UUID instrumentId, LocalDate openedSession) {
    }

    record RunSignal(LocalDate session, String action, BigDecimal referencePrice) {
    }

    record OrderView(UUID orderId, UUID clientOrderId, UUID runId, UUID instrumentId,
                     String side, BigDecimal quantity, LocalDate requestedSession,
                     String status, UUID fillTradeId, String rejectionReason) {
    }
}
