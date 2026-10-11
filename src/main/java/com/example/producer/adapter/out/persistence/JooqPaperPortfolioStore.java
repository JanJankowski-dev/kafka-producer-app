package com.example.producer.adapter.out.persistence;

import com.example.producer.application.port.out.PaperPortfolioStorePort;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import static com.example.producer.jooq.tables.MarketInstruments.MARKET_INSTRUMENTS;
import static com.example.producer.jooq.tables.PaperCash.PAPER_CASH;
import static com.example.producer.jooq.tables.PaperPortfolios.PAPER_PORTFOLIOS;
import static com.example.producer.jooq.tables.PaperPositions.PAPER_POSITIONS;
import static com.example.producer.jooq.tables.PaperTrades.PAPER_TRADES;
import static com.example.producer.jooq.tables.ScreeningRuns.SCREENING_RUNS;
import static com.example.producer.jooq.tables.ScreeningSignals.SCREENING_SIGNALS;

@Repository
public class JooqPaperPortfolioStore implements PaperPortfolioStorePort {
    private static final BigDecimal STARTING_CASH = new BigDecimal("10000.00000000");
    private final DSLContext dsl;

    public JooqPaperPortfolioStore(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public UUID create() {
        UUID id = UUID.randomUUID();
        dsl.insertInto(PAPER_PORTFOLIOS).set(PAPER_PORTFOLIOS.PORTFOLIO_ID, id).execute();
        for (String currency : List.of("PLN", "USD")) {
            dsl.insertInto(PAPER_CASH)
                    .set(PAPER_CASH.PORTFOLIO_ID, id)
                    .set(PAPER_CASH.CURRENCY, currency)
                    .set(PAPER_CASH.BALANCE, STARTING_CASH)
                    .execute();
        }
        return id;
    }

    @Override
    @Transactional(readOnly = true)
    public PortfolioView view(UUID portfolioId) {
        var portfolio = dsl.selectFrom(PAPER_PORTFOLIOS)
                .where(PAPER_PORTFOLIOS.PORTFOLIO_ID.eq(portfolioId)).fetchOne();
        if (portfolio == null) {
            throw new IllegalArgumentException("Paper portfolio not found");
        }
        var cash = dsl.selectFrom(PAPER_CASH)
                .where(PAPER_CASH.PORTFOLIO_ID.eq(portfolioId))
                .orderBy(PAPER_CASH.CURRENCY)
                .fetch(record -> new CashView(record.getCurrency(), record.getBalance()));
        var positions = dsl.selectFrom(PAPER_POSITIONS)
                .where(PAPER_POSITIONS.PORTFOLIO_ID.eq(portfolioId))
                .orderBy(PAPER_POSITIONS.SYMBOL)
                .fetch(record -> new PositionView(record.getInstrumentId(), record.getSymbol(),
                        record.getCurrency(), record.getQuantity(), record.getAveragePrice()));
        var trades = dsl.selectFrom(PAPER_TRADES)
                .where(PAPER_TRADES.PORTFOLIO_ID.eq(portfolioId))
                .orderBy(PAPER_TRADES.EXECUTED_AT.desc(), PAPER_TRADES.TRADE_ID.desc())
                .limit(50)
                .fetch(record -> tradeView(record.getTradeId(), record.getClientOrderId(),
                        record.getRunId(), record.getInstrumentId(), record.getAction(),
                        record.getQuantity(), record.getPrice(), record.getCurrency(),
                        record.getExecutionSession()));
        return new PortfolioView(portfolioId, portfolio.getLastSession(), cash, positions, trades);
    }

    @Override
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public TradeView trade(UUID portfolioId, TradeCommand command) {
        var portfolio = dsl.selectFrom(PAPER_PORTFOLIOS)
                .where(PAPER_PORTFOLIOS.PORTFOLIO_ID.eq(portfolioId))
                .forUpdate().fetchOne();
        if (portfolio == null) {
            throw new IllegalArgumentException("Paper portfolio not found");
        }
        var previous = dsl.selectFrom(PAPER_TRADES)
                .where(PAPER_TRADES.PORTFOLIO_ID.eq(portfolioId))
                .and(PAPER_TRADES.CLIENT_ORDER_ID.eq(command.clientOrderId()))
                .fetchOne();
        if (previous != null) {
            if (!previous.getRunId().equals(command.runId())
                    || !previous.getInstrumentId().equals(command.instrumentId())
                    || !previous.getAction().equals(command.side())
                    || previous.getQuantity().compareTo(command.quantity()) != 0
                    || (command.executionPrice() != null
                            && (previous.getPrice().compareTo(command.executionPrice()) != 0
                                || !command.executionSession().equals(previous.getExecutionSession())))) {
                throw new IllegalStateException("Client order ID was already used for a different trade");
            }
            return tradeView(previous.getTradeId(), previous.getClientOrderId(),
                    previous.getRunId(), previous.getInstrumentId(), previous.getAction(),
                    previous.getQuantity(), previous.getPrice(), previous.getCurrency(),
                    previous.getExecutionSession());
        }

        var run = dsl.selectFrom(SCREENING_RUNS)
                .where(SCREENING_RUNS.RUN_ID.eq(command.runId())).fetchOne();
        if (run == null) {
            throw new IllegalArgumentException("Screening run not found");
        }
        var signal = dsl.selectFrom(SCREENING_SIGNALS)
                .where(SCREENING_SIGNALS.RUN_ID.eq(command.runId()))
                .and(SCREENING_SIGNALS.INSTRUMENT_ID.eq(command.instrumentId()))
                .fetchOne();
        if (signal == null || signal.getLatestAdjustedClose() == null) {
            throw new IllegalArgumentException("No reference price for this instrument in the saved run");
        }
        var instrument = dsl.selectFrom(MARKET_INSTRUMENTS)
                .where(MARKET_INSTRUMENTS.SNAPSHOT_ID.eq(run.getSnapshotId()))
                .and(MARKET_INSTRUMENTS.INSTRUMENT_ID.eq(command.instrumentId()))
                .fetchOne();
        if (instrument == null) {
            throw new IllegalStateException("Saved run has no market instrument");
        }
        BigDecimal price = command.executionPrice() == null
                ? signal.getLatestAdjustedClose() : command.executionPrice();
        BigDecimal notional = price.multiply(command.quantity()).setScale(8, RoundingMode.HALF_UP);
        if (notional.signum() <= 0) {
            throw new IllegalArgumentException("Paper trade value is too small");
        }
        var cash = dsl.selectFrom(PAPER_CASH)
                .where(PAPER_CASH.PORTFOLIO_ID.eq(portfolioId))
                .and(PAPER_CASH.CURRENCY.eq(instrument.getCurrency()))
                .fetchOne();
        if (cash == null) {
            throw new IllegalArgumentException("Portfolio has no cash in " + instrument.getCurrency());
        }
        var position = dsl.selectFrom(PAPER_POSITIONS)
                .where(PAPER_POSITIONS.PORTFOLIO_ID.eq(portfolioId))
                .and(PAPER_POSITIONS.INSTRUMENT_ID.eq(command.instrumentId()))
                .fetchOne();

        if (command.side().equals("BUY")) {
            if (cash.getBalance().compareTo(notional) < 0) {
                throw new IllegalArgumentException("Insufficient paper cash");
            }
            dsl.update(PAPER_CASH)
                    .set(PAPER_CASH.BALANCE, cash.getBalance().subtract(notional))
                    .where(PAPER_CASH.PORTFOLIO_ID.eq(portfolioId))
                    .and(PAPER_CASH.CURRENCY.eq(instrument.getCurrency())).execute();
            if (position == null) {
                dsl.insertInto(PAPER_POSITIONS)
                        .set(PAPER_POSITIONS.PORTFOLIO_ID, portfolioId)
                        .set(PAPER_POSITIONS.INSTRUMENT_ID, command.instrumentId())
                        .set(PAPER_POSITIONS.SYMBOL, instrument.getSymbol())
                        .set(PAPER_POSITIONS.CURRENCY, instrument.getCurrency())
                        .set(PAPER_POSITIONS.QUANTITY, command.quantity())
                        .set(PAPER_POSITIONS.AVERAGE_PRICE, price)
                        .set(PAPER_POSITIONS.OPENED_SESSION, command.executionSession())
                        .execute();
            } else {
                BigDecimal totalQuantity = position.getQuantity().add(command.quantity());
                BigDecimal average = position.getAveragePrice().multiply(position.getQuantity())
                        .add(notional).divide(totalQuantity, 8, RoundingMode.HALF_UP);
                dsl.update(PAPER_POSITIONS)
                        .set(PAPER_POSITIONS.QUANTITY, totalQuantity)
                        .set(PAPER_POSITIONS.AVERAGE_PRICE, average)
                        .where(PAPER_POSITIONS.PORTFOLIO_ID.eq(portfolioId))
                        .and(PAPER_POSITIONS.INSTRUMENT_ID.eq(command.instrumentId())).execute();
            }
        } else {
            if (position == null || position.getQuantity().compareTo(command.quantity()) < 0) {
                throw new IllegalArgumentException("Insufficient paper position");
            }
            if (!position.getCurrency().equals(instrument.getCurrency())) {
                throw new IllegalArgumentException("Position currency does not match saved market data");
            }
            dsl.update(PAPER_CASH)
                    .set(PAPER_CASH.BALANCE, cash.getBalance().add(notional))
                    .where(PAPER_CASH.PORTFOLIO_ID.eq(portfolioId))
                    .and(PAPER_CASH.CURRENCY.eq(instrument.getCurrency())).execute();
            BigDecimal remaining = position.getQuantity().subtract(command.quantity());
            if (remaining.signum() == 0) {
                dsl.deleteFrom(PAPER_POSITIONS)
                        .where(PAPER_POSITIONS.PORTFOLIO_ID.eq(portfolioId))
                        .and(PAPER_POSITIONS.INSTRUMENT_ID.eq(command.instrumentId())).execute();
            } else {
                dsl.update(PAPER_POSITIONS)
                        .set(PAPER_POSITIONS.QUANTITY, remaining)
                        .where(PAPER_POSITIONS.PORTFOLIO_ID.eq(portfolioId))
                        .and(PAPER_POSITIONS.INSTRUMENT_ID.eq(command.instrumentId())).execute();
            }
        }

        UUID tradeId = UUID.randomUUID();
        dsl.insertInto(PAPER_TRADES)
                .set(PAPER_TRADES.TRADE_ID, tradeId)
                .set(PAPER_TRADES.PORTFOLIO_ID, portfolioId)
                .set(PAPER_TRADES.CLIENT_ORDER_ID, command.clientOrderId())
                .set(PAPER_TRADES.RUN_ID, command.runId())
                .set(PAPER_TRADES.INSTRUMENT_ID, command.instrumentId())
                .set(PAPER_TRADES.ACTION, command.side())
                .set(PAPER_TRADES.QUANTITY, command.quantity())
                .set(PAPER_TRADES.PRICE, price)
                .set(PAPER_TRADES.CURRENCY, instrument.getCurrency())
                .set(PAPER_TRADES.EXECUTION_SESSION, command.executionSession())
                .execute();
        return tradeView(tradeId, command.clientOrderId(), command.runId(), command.instrumentId(),
                command.side(), command.quantity(), price, instrument.getCurrency(),
                command.executionSession());
    }

    private TradeView tradeView(UUID tradeId, UUID clientOrderId, UUID runId, UUID instrumentId,
                                String side, BigDecimal quantity, BigDecimal price, String currency,
                                java.time.LocalDate executionSession) {
        return new TradeView(tradeId, clientOrderId, runId, instrumentId, side, quantity,
                price, currency, executionSession);
    }
}
