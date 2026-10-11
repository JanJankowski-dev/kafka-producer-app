package com.example.producer.application.service;

import com.example.producer.application.port.out.AiDecisionPort;
import com.example.producer.application.port.out.HistoricalPricesPort.DailyPrice;
import com.example.producer.application.port.out.MarketImportStorePort;
import com.example.producer.application.port.out.StrategyComparisonStorePort;
import com.example.producer.domain.strategy.AssetType;
import com.example.producer.domain.strategy.Breakout20Strategy;
import com.example.producer.domain.strategy.DailyClose;
import com.example.producer.domain.strategy.InstrumentHistory;
import com.example.producer.domain.strategy.OpenPosition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.example.producer.jooq.tables.PaperAutoDecisions.PAPER_AUTO_DECISIONS;
import static com.example.producer.jooq.tables.PaperAutoOrders.PAPER_AUTO_ORDERS;
import static com.example.producer.jooq.tables.PaperCash.PAPER_CASH;
import static com.example.producer.jooq.tables.PaperPortfolios.PAPER_PORTFOLIOS;
import static com.example.producer.jooq.tables.PaperPositions.PAPER_POSITIONS;

@Service
public class AutoPaperTradingService {
    private static final BigDecimal MAX_POSITION_FRACTION = new BigDecimal("0.10");
    private static final int MAX_POSITIONS = 3;
    private final MarketImportStorePort imports;
    private final StrategyComparisonStorePort comparisons;
    private final AiDecisionPort ai;
    private final DSLContext dsl;
    private final TransactionTemplate transactions;
    private final Breakout20Strategy traditional = new Breakout20Strategy();

    public AutoPaperTradingService(MarketImportStorePort imports, StrategyComparisonStorePort comparisons,
                                   AiDecisionPort ai, DSLContext dsl, TransactionTemplate transactions) {
        this.imports = imports;
        this.comparisons = comparisons;
        this.ai = ai;
        this.dsl = dsl;
        this.transactions = transactions;
    }

    public Result runLatest() {
        var batch = imports.latestHistories();
        var comparison = comparisons.latest();
        if (comparison == null) {
            comparison = comparisons.createOrFind(batch.status().batchId(), Breakout20Strategy.VERSION);
        }
        var traditionalResult = process(comparison, batch, comparison.traditionalPortfolioId(),
                "TRADITIONAL", List.of());
        List<AiDecisionPort.Decision> aiDecisions = List.of();
        if (needsSession(comparison.aiPortfolioId(), batch.status().latestCommonSession()) && ai.configured()) {
            try {
                aiDecisions = ai.decide(batch.status().latestCommonSession(),
                        candidates(comparison.aiPortfolioId(), batch));
            } catch (RuntimeException ignored) {
                // A model outage is a HOLD, never a trade. Details stay out of public responses.
            }
        }
        var aiResult = process(comparison, batch, comparison.aiPortfolioId(), "AI", aiDecisions);
        return new Result(batch.status().latestCommonSession(), traditionalResult, aiResult);
    }

    private boolean needsSession(UUID portfolioId, LocalDate session) {
        LocalDate last = dsl.select(PAPER_PORTFOLIOS.LAST_SESSION).from(PAPER_PORTFOLIOS)
                .where(PAPER_PORTFOLIOS.PORTFOLIO_ID.eq(portfolioId)).fetchOne(PAPER_PORTFOLIOS.LAST_SESSION);
        return last == null || last.isBefore(session);
    }

    private List<AiDecisionPort.Candidate> candidates(UUID portfolioId,
                                                       MarketImportStorePort.ImportedBatch batch) {
        Map<UUID, LocalDate> positions = dsl.select(PAPER_POSITIONS.INSTRUMENT_ID,
                        PAPER_POSITIONS.OPENED_SESSION).from(PAPER_POSITIONS)
                .where(PAPER_POSITIONS.PORTFOLIO_ID.eq(portfolioId))
                .fetchMap(PAPER_POSITIONS.INSTRUMENT_ID, PAPER_POSITIONS.OPENED_SESSION);
        var result = new ArrayList<AiDecisionPort.Candidate>();
        for (var history : batch.histories()) {
            var prices = history.prices();
            var last = prices.get(prices.size() - 1);
            BigDecimal first = prices.get(prices.size() - 21).adjustedClose();
            BigDecimal returnPct = last.adjustedClose().subtract(first)
                    .multiply(BigDecimal.valueOf(100)).divide(first, 4, RoundingMode.HALF_UP);
            LocalDate opened = positions.get(history.instrument().instrumentId());
            int held = opened == null ? 0 : (int) prices.stream()
                    .filter(price -> price.sessionDate().isAfter(opened)).count();
            result.add(new AiDecisionPort.Candidate(history.instrument().symbol(),
                    last.adjustedClose(), returnPct, opened != null, held));
        }
        return List.copyOf(result);
    }

    private int process(StrategyComparisonStorePort.ComparisonView comparison,
                        MarketImportStorePort.ImportedBatch batch, UUID portfolioId,
                        String engine, List<AiDecisionPort.Decision> proposals) {
        return transactions.execute(status -> processLocked(comparison, batch, portfolioId, engine, proposals));
    }

    private int processLocked(StrategyComparisonStorePort.ComparisonView comparison,
                              MarketImportStorePort.ImportedBatch batch, UUID portfolioId,
                              String engine, List<AiDecisionPort.Decision> proposals) {
        var portfolio = dsl.selectFrom(PAPER_PORTFOLIOS)
                .where(PAPER_PORTFOLIOS.PORTFOLIO_ID.eq(portfolioId)).forUpdate().fetchOne();
        if (portfolio == null) throw new IllegalStateException("Comparison portfolio is missing");
        LocalDate current = batch.status().latestCommonSession();
        LocalDate previous = portfolio.getLastSession();
        if (previous != null && !previous.isBefore(current)) return 0;
        List<LocalDate> dates = batch.histories().get(0).prices().stream()
                .map(DailyPrice::sessionDate).toList();
        if (previous != null && !dates.contains(previous)) {
            throw new IllegalStateException("Previous portfolio session is outside imported history");
        }
        if (previous != null && dates.indexOf(current) != dates.indexOf(previous) + 1
                && dsl.fetchExists(dsl.selectOne().from(PAPER_POSITIONS)
                    .where(PAPER_POSITIONS.PORTFOLIO_ID.eq(portfolioId)))) {
            throw new IllegalStateException("Missed sessions with open positions require reconciliation");
        }
        if (previous != null) {
            boolean adjacent = dates.indexOf(current) == dates.indexOf(previous) + 1;
            if (adjacent) {
                applyCorporateActions(portfolioId, batch, current);
            }
            settlePending(portfolioId, batch, current, adjacent);
        }
        var held = dsl.selectFrom(PAPER_POSITIONS)
                .where(PAPER_POSITIONS.PORTFOLIO_ID.eq(portfolioId))
                .fetchMap(PAPER_POSITIONS.INSTRUMENT_ID);
        Map<String, AiDecisionPort.Decision> aiBySymbol = new HashMap<>();
        if (proposals.size() == batch.histories().size()) {
            Set<String> symbols = new HashSet<>();
            for (var proposal : proposals) {
                if (proposal != null && symbols.add(proposal.symbol())
                        && Set.of("BUY", "SELL", "HOLD").contains(proposal.action())
                        && proposal.reason() != null && !proposal.reason().isBlank()
                        && proposal.reason().length() <= 400) {
                    aiBySymbol.put(proposal.symbol(), proposal);
                }
            }
            if (aiBySymbol.size() != batch.histories().size()
                    || batch.histories().stream().anyMatch(h -> !aiBySymbol.containsKey(h.instrument().symbol()))) {
                aiBySymbol.clear();
            }
        }
        int decisions = 0;
        for (var history : batch.histories()) {
            var instrument = history.instrument();
            var position = held.get(instrument.instrumentId());
            String action;
            String reason;
            if (engine.equals("TRADITIONAL")) {
                var closes = history.prices().stream()
                        .map(p -> new DailyClose(p.sessionDate(), p.adjustedClose())).toList();
                int heldSessions = position == null || position.getOpenedSession() == null ? 0
                        : (int) dates.stream().filter(d -> d.isAfter(position.getOpenedSession())).count();
                var signal = traditional.evaluate(new InstrumentHistory(instrument.instrumentId(),
                        AssetType.STOCK, closes, position == null ? null : new OpenPosition(heldSessions)), current);
                action = signal.action().name();
                reason = signal.reasonCode();
            } else {
                var proposed = aiBySymbol.get(instrument.symbol());
                action = proposed == null ? "HOLD" : proposed.action();
                reason = proposed == null ? (ai.configured() ? "PROVIDER_UNAVAILABLE"
                        : "DATA_SHARING_DISABLED".equals(ai.modelVersion())
                        ? "DATA_SHARING_DISABLED" : "PROVIDER_NOT_CONFIGURED")
                        : proposed.reason();
            }
            if (action.equals("NO_ACTION")) action = "HOLD";
            if ((action.equals("BUY") && position != null)
                    || (action.equals("SELL") && position == null)) {
                action = "HOLD";
                reason = "POSITION_STATE_REJECTED";
            }
            UUID id = UUID.randomUUID();
            dsl.insertInto(PAPER_AUTO_DECISIONS)
                    .set(PAPER_AUTO_DECISIONS.DECISION_ID, id)
                    .set(PAPER_AUTO_DECISIONS.COMPARISON_ID, comparison.comparisonId())
                    .set(PAPER_AUTO_DECISIONS.PORTFOLIO_ID, portfolioId)
                    .set(PAPER_AUTO_DECISIONS.MARKET_IMPORT_BATCH_ID, batch.status().batchId())
                    .set(PAPER_AUTO_DECISIONS.SESSION_DATE, current)
                    .set(PAPER_AUTO_DECISIONS.INSTRUMENT_ID, instrument.instrumentId())
                    .set(PAPER_AUTO_DECISIONS.SYMBOL, instrument.symbol())
                    .set(PAPER_AUTO_DECISIONS.ENGINE, engine)
                    .set(PAPER_AUTO_DECISIONS.ENGINE_VERSION,
                            engine.equals("AI") ? ai.modelVersion() : Breakout20Strategy.VERSION)
                    .set(PAPER_AUTO_DECISIONS.ACTION, action)
                    .set(PAPER_AUTO_DECISIONS.REASON, reason)
                    .execute();
            if (!action.equals("HOLD")) {
                dsl.insertInto(PAPER_AUTO_ORDERS)
                        .set(PAPER_AUTO_ORDERS.ORDER_ID, UUID.randomUUID())
                        .set(PAPER_AUTO_ORDERS.DECISION_ID, id)
                        .set(PAPER_AUTO_ORDERS.PORTFOLIO_ID, portfolioId)
                        .set(PAPER_AUTO_ORDERS.INSTRUMENT_ID, instrument.instrumentId())
                        .set(PAPER_AUTO_ORDERS.SYMBOL, instrument.symbol())
                        .set(PAPER_AUTO_ORDERS.SIDE, action)
                        .set(PAPER_AUTO_ORDERS.REQUESTED_SESSION, current)
                        .set(PAPER_AUTO_ORDERS.STATUS, "PENDING")
                        .execute();
            }
            decisions++;
        }
        dsl.update(PAPER_PORTFOLIOS).set(PAPER_PORTFOLIOS.LAST_SESSION, current)
                .where(PAPER_PORTFOLIOS.PORTFOLIO_ID.eq(portfolioId)).execute();
        return decisions;
    }

    private void applyCorporateActions(UUID portfolioId, MarketImportStorePort.ImportedBatch batch,
                                       LocalDate current) {
        var held = dsl.selectFrom(PAPER_POSITIONS)
                .where(PAPER_POSITIONS.PORTFOLIO_ID.eq(portfolioId)).fetch();
        for (var position : held) {
            DailyPrice price = price(batch, position.getInstrumentId(), current);
            if (price == null) throw new IllegalStateException("Missing corporate action data");
            if (price.dividendCash().signum() > 0
                    && price.splitFactor().compareTo(BigDecimal.ONE) != 0) {
                throw new IllegalStateException("Combined split and dividend require manual reconciliation");
            }
            if (price.dividendCash().signum() > 0) {
                BigDecimal dividend = position.getQuantity().multiply(price.dividendCash())
                        .setScale(8, RoundingMode.HALF_UP);
                dsl.update(PAPER_CASH).set(PAPER_CASH.BALANCE, PAPER_CASH.BALANCE.add(dividend))
                        .where(PAPER_CASH.PORTFOLIO_ID.eq(portfolioId))
                        .and(PAPER_CASH.CURRENCY.eq("USD")).execute();
            }
            if (price.splitFactor().compareTo(BigDecimal.ONE) != 0) {
                BigDecimal quantity = position.getQuantity().multiply(price.splitFactor())
                        .setScale(8, RoundingMode.UNNECESSARY);
                BigDecimal average = position.getAveragePrice().divide(price.splitFactor(),
                        8, RoundingMode.HALF_UP);
                dsl.update(PAPER_POSITIONS).set(PAPER_POSITIONS.QUANTITY, quantity)
                        .set(PAPER_POSITIONS.AVERAGE_PRICE, average)
                        .where(PAPER_POSITIONS.PORTFOLIO_ID.eq(portfolioId))
                        .and(PAPER_POSITIONS.INSTRUMENT_ID.eq(position.getInstrumentId())).execute();
            }
        }
    }

    private void settlePending(UUID portfolioId, MarketImportStorePort.ImportedBatch batch,
                               LocalDate current, boolean adjacent) {
        var orders = dsl.selectFrom(PAPER_AUTO_ORDERS)
                .where(PAPER_AUTO_ORDERS.PORTFOLIO_ID.eq(portfolioId))
                .and(PAPER_AUTO_ORDERS.STATUS.eq("PENDING"))
                .orderBy(PAPER_AUTO_ORDERS.SIDE.desc(), PAPER_AUTO_ORDERS.SYMBOL)
                .fetch();
        for (var order : orders) {
            if (!adjacent || !order.getRequestedSession().isBefore(current)) {
                reject(order.getOrderId(), "MISSED_NEXT_SESSION");
                continue;
            }
            DailyPrice price = price(batch, order.getInstrumentId(), current);
            if (price == null || price.rawOpen() == null) {
                reject(order.getOrderId(), "NO_OPEN_PRICE");
                continue;
            }
            var position = dsl.selectFrom(PAPER_POSITIONS)
                    .where(PAPER_POSITIONS.PORTFOLIO_ID.eq(portfolioId))
                    .and(PAPER_POSITIONS.INSTRUMENT_ID.eq(order.getInstrumentId())).fetchOne();
            BigDecimal cash = dsl.select(PAPER_CASH.BALANCE).from(PAPER_CASH)
                    .where(PAPER_CASH.PORTFOLIO_ID.eq(portfolioId))
                    .and(PAPER_CASH.CURRENCY.eq("USD")).fetchOne(PAPER_CASH.BALANCE);
            if (cash == null) throw new IllegalStateException("USD cash account is missing");
            BigDecimal quantity;
            if (order.getSide().equals("SELL")) {
                if (position == null) { reject(order.getOrderId(), "NO_POSITION"); continue; }
                quantity = position.getQuantity();
            } else {
                int count = dsl.fetchCount(PAPER_POSITIONS,
                        PAPER_POSITIONS.PORTFOLIO_ID.eq(portfolioId));
                if (position != null || count >= MAX_POSITIONS) {
                    reject(order.getOrderId(), "POSITION_LIMIT"); continue;
                }
                BigDecimal equity = cash.add(openPositionValue(portfolioId, batch, current));
                BigDecimal budget = equity.multiply(MAX_POSITION_FRACTION).min(cash);
                quantity = budget.divide(price.rawOpen(), 8, RoundingMode.DOWN);
                if (quantity.signum() <= 0) { reject(order.getOrderId(), "NO_CASH"); continue; }
            }
            BigDecimal value = price.rawOpen().multiply(quantity).setScale(8, RoundingMode.HALF_UP);
            if (order.getSide().equals("BUY")) {
                if (value.compareTo(cash) > 0) { reject(order.getOrderId(), "NO_CASH"); continue; }
                dsl.update(PAPER_CASH).set(PAPER_CASH.BALANCE, cash.subtract(value))
                        .where(PAPER_CASH.PORTFOLIO_ID.eq(portfolioId))
                        .and(PAPER_CASH.CURRENCY.eq("USD")).execute();
                dsl.insertInto(PAPER_POSITIONS)
                        .set(PAPER_POSITIONS.PORTFOLIO_ID, portfolioId)
                        .set(PAPER_POSITIONS.INSTRUMENT_ID, order.getInstrumentId())
                        .set(PAPER_POSITIONS.SYMBOL, order.getSymbol())
                        .set(PAPER_POSITIONS.CURRENCY, "USD")
                        .set(PAPER_POSITIONS.QUANTITY, quantity)
                        .set(PAPER_POSITIONS.AVERAGE_PRICE, price.rawOpen())
                        .set(PAPER_POSITIONS.OPENED_SESSION, current).execute();
            } else {
                dsl.update(PAPER_CASH).set(PAPER_CASH.BALANCE, cash.add(value))
                        .where(PAPER_CASH.PORTFOLIO_ID.eq(portfolioId))
                        .and(PAPER_CASH.CURRENCY.eq("USD")).execute();
                dsl.deleteFrom(PAPER_POSITIONS)
                        .where(PAPER_POSITIONS.PORTFOLIO_ID.eq(portfolioId))
                        .and(PAPER_POSITIONS.INSTRUMENT_ID.eq(order.getInstrumentId())).execute();
            }
            dsl.update(PAPER_AUTO_ORDERS).set(PAPER_AUTO_ORDERS.STATUS, "FILLED")
                    .set(PAPER_AUTO_ORDERS.QUANTITY, quantity)
                    .set(PAPER_AUTO_ORDERS.FILL_PRICE, price.rawOpen())
                    .set(PAPER_AUTO_ORDERS.EXECUTION_SESSION, current)
                    .where(PAPER_AUTO_ORDERS.ORDER_ID.eq(order.getOrderId())).execute();
        }
    }

    private BigDecimal openPositionValue(UUID portfolioId, MarketImportStorePort.ImportedBatch batch,
                                         LocalDate current) {
        BigDecimal total = BigDecimal.ZERO;
        for (var position : dsl.selectFrom(PAPER_POSITIONS)
                .where(PAPER_POSITIONS.PORTFOLIO_ID.eq(portfolioId)).fetch()) {
            DailyPrice price = price(batch, position.getInstrumentId(), current);
            if (price == null) throw new IllegalStateException("Missing open price for held instrument");
            total = total.add(position.getQuantity().multiply(price.rawOpen()));
        }
        return total;
    }

    private DailyPrice price(MarketImportStorePort.ImportedBatch batch, UUID instrumentId,
                             LocalDate session) {
        return batch.histories().stream()
                .filter(h -> h.instrument().instrumentId().equals(instrumentId))
                .flatMap(h -> h.prices().stream())
                .filter(p -> p.sessionDate().equals(session)).findFirst().orElse(null);
    }

    private void reject(UUID orderId, String reason) {
        dsl.update(PAPER_AUTO_ORDERS).set(PAPER_AUTO_ORDERS.STATUS, "REJECTED")
                .set(PAPER_AUTO_ORDERS.REJECTION_REASON, reason)
                .where(PAPER_AUTO_ORDERS.ORDER_ID.eq(orderId)).execute();
    }

    public record Result(LocalDate session, int traditionalDecisions, int aiDecisions) { }

    public Activity latestActivity() {
        var comparison = comparisons.latest();
        if (comparison == null) return new Activity(List.of(), List.of(), ai.configured());
        var decisions = dsl.selectFrom(PAPER_AUTO_DECISIONS)
                .where(PAPER_AUTO_DECISIONS.COMPARISON_ID.eq(comparison.comparisonId()))
                .orderBy(PAPER_AUTO_DECISIONS.SESSION_DATE.desc(), PAPER_AUTO_DECISIONS.ENGINE,
                        PAPER_AUTO_DECISIONS.SYMBOL)
                .limit(80)
                .fetch(row -> new DecisionView(row.getPortfolioId(), row.getSessionDate(),
                        row.getEngine(), row.getEngineVersion(), row.getSymbol(),
                        row.getAction(), row.getReason()));
        var orders = dsl.selectFrom(PAPER_AUTO_ORDERS)
                .where(PAPER_AUTO_ORDERS.PORTFOLIO_ID.in(
                        comparison.traditionalPortfolioId(), comparison.aiPortfolioId()))
                .orderBy(PAPER_AUTO_ORDERS.REQUESTED_SESSION.desc(), PAPER_AUTO_ORDERS.SYMBOL)
                .limit(80)
                .fetch(row -> new OrderView(row.getPortfolioId(), row.getSymbol(), row.getSide(),
                        row.getRequestedSession(), row.getStatus(), row.getQuantity(),
                        row.getFillPrice(), row.getExecutionSession(), row.getRejectionReason()));
        return new Activity(decisions, orders, ai.configured());
    }

    public record Activity(List<DecisionView> decisions, List<OrderView> orders,
                           boolean aiConfigured) { }
    public record DecisionView(UUID portfolioId, LocalDate session, String engine,
                               String engineVersion, String symbol, String action,
                               String reason) { }
    public record OrderView(UUID portfolioId, String symbol, String side,
                            LocalDate requestedSession, String status,
                            BigDecimal quantity, BigDecimal fillPrice,
                            LocalDate executionSession, String rejectionReason) { }
}
