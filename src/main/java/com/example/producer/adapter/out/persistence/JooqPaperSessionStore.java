package com.example.producer.adapter.out.persistence;

import com.example.producer.application.port.out.PaperSessionStorePort;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.example.producer.jooq.tables.MarketSnapshots.MARKET_SNAPSHOTS;
import static com.example.producer.jooq.tables.PaperOrders.PAPER_ORDERS;
import static com.example.producer.jooq.tables.PaperPortfolios.PAPER_PORTFOLIOS;
import static com.example.producer.jooq.tables.PaperPositions.PAPER_POSITIONS;
import static com.example.producer.jooq.tables.ScreeningRuns.SCREENING_RUNS;
import static com.example.producer.jooq.tables.ScreeningSignals.SCREENING_SIGNALS;

@Repository
public class JooqPaperSessionStore implements PaperSessionStorePort {
    private final DSLContext dsl;

    public JooqPaperSessionStore(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public LocalDate lockLastSession(UUID portfolioId) {
        var portfolio = dsl.selectFrom(PAPER_PORTFOLIOS)
                .where(PAPER_PORTFOLIOS.PORTFOLIO_ID.eq(portfolioId)).forUpdate().fetchOne();
        if (portfolio == null) {
            throw new IllegalArgumentException("Paper portfolio not found");
        }
        return portfolio.getLastSession();
    }

    @Override
    public void setLastSession(UUID portfolioId, LocalDate session) {
        dsl.update(PAPER_PORTFOLIOS).set(PAPER_PORTFOLIOS.LAST_SESSION, session)
                .where(PAPER_PORTFOLIOS.PORTFOLIO_ID.eq(portfolioId)).execute();
    }

    @Override
    public List<HeldPosition> positions(UUID portfolioId) {
        return dsl.selectFrom(PAPER_POSITIONS)
                .where(PAPER_POSITIONS.PORTFOLIO_ID.eq(portfolioId))
                .fetch(record -> new HeldPosition(record.getInstrumentId(), record.getOpenedSession()));
    }

    @Override
    public List<OrderView> pendingOrders(UUID portfolioId) {
        return dsl.selectFrom(PAPER_ORDERS)
                .where(PAPER_ORDERS.PORTFOLIO_ID.eq(portfolioId))
                .and(PAPER_ORDERS.STATUS.eq("PENDING"))
                .orderBy(PAPER_ORDERS.CREATED_AT, PAPER_ORDERS.ORDER_ID)
                .fetch(this::view);
    }

    @Override
    public List<OrderView> orders(UUID portfolioId) {
        return dsl.selectFrom(PAPER_ORDERS)
                .where(PAPER_ORDERS.PORTFOLIO_ID.eq(portfolioId))
                .orderBy(PAPER_ORDERS.CREATED_AT.desc(), PAPER_ORDERS.ORDER_ID.desc())
                .limit(50).fetch(this::view);
    }

    @Override
    public OrderView findOrder(UUID portfolioId, UUID clientOrderId) {
        var record = dsl.selectFrom(PAPER_ORDERS)
                .where(PAPER_ORDERS.PORTFOLIO_ID.eq(portfolioId))
                .and(PAPER_ORDERS.CLIENT_ORDER_ID.eq(clientOrderId)).fetchOne();
        return record == null ? null : view(record);
    }

    @Override
    public RunSignal runSignal(UUID portfolioId, UUID runId, UUID instrumentId) {
        var record = dsl.select(MARKET_SNAPSHOTS.AS_OF_SESSION, SCREENING_SIGNALS.ACTION,
                        SCREENING_SIGNALS.LATEST_ADJUSTED_CLOSE)
                .from(SCREENING_RUNS)
                .join(MARKET_SNAPSHOTS).on(SCREENING_RUNS.SNAPSHOT_ID.eq(MARKET_SNAPSHOTS.SNAPSHOT_ID))
                .join(SCREENING_SIGNALS).on(SCREENING_SIGNALS.RUN_ID.eq(SCREENING_RUNS.RUN_ID))
                .where(SCREENING_RUNS.RUN_ID.eq(runId))
                .and(SCREENING_RUNS.PORTFOLIO_ID.eq(portfolioId))
                .and(SCREENING_SIGNALS.INSTRUMENT_ID.eq(instrumentId))
                .fetchOne();
        return record == null ? null : new RunSignal(record.value1(), record.value2(), record.value3());
    }

    @Override
    public OrderView insertPending(UUID portfolioId, UUID clientOrderId, UUID runId,
                                   UUID instrumentId, String side, BigDecimal quantity,
                                   LocalDate requestedSession) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(PAPER_ORDERS)
                .set(PAPER_ORDERS.ORDER_ID, id)
                .set(PAPER_ORDERS.PORTFOLIO_ID, portfolioId)
                .set(PAPER_ORDERS.CLIENT_ORDER_ID, clientOrderId)
                .set(PAPER_ORDERS.RUN_ID, runId)
                .set(PAPER_ORDERS.INSTRUMENT_ID, instrumentId)
                .set(PAPER_ORDERS.SIDE, side)
                .set(PAPER_ORDERS.QUANTITY, quantity)
                .set(PAPER_ORDERS.REQUESTED_SESSION, requestedSession)
                .set(PAPER_ORDERS.STATUS, "PENDING")
                .execute();
        return findOrder(portfolioId, clientOrderId);
    }

    @Override
    public void markFilled(UUID orderId, UUID tradeId) {
        dsl.update(PAPER_ORDERS).set(PAPER_ORDERS.STATUS, "FILLED")
                .set(PAPER_ORDERS.FILL_TRADE_ID, tradeId)
                .where(PAPER_ORDERS.ORDER_ID.eq(orderId)).execute();
    }

    @Override
    public void markRejected(UUID orderId, String reason) {
        dsl.update(PAPER_ORDERS).set(PAPER_ORDERS.STATUS, "REJECTED")
                .set(PAPER_ORDERS.REJECTION_REASON, reason)
                .where(PAPER_ORDERS.ORDER_ID.eq(orderId)).execute();
    }

    private OrderView view(com.example.producer.jooq.tables.records.PaperOrdersRecord record) {
        return new OrderView(record.getOrderId(), record.getClientOrderId(), record.getRunId(),
                record.getInstrumentId(), record.getSide(), record.getQuantity(),
                record.getRequestedSession(), record.getStatus(), record.getFillTradeId(),
                record.getRejectionReason());
    }
}
