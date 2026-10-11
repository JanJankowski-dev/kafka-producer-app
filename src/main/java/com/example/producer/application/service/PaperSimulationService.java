package com.example.producer.application.service;

import com.example.producer.application.port.in.AvailableMarketScreeningResult;
import com.example.producer.application.port.out.MarketDataSnapshot;
import com.example.producer.application.port.out.MarketInstrument;
import com.example.producer.application.port.out.MarketResearchStorePort;
import com.example.producer.application.port.out.PaperPortfolioStorePort;
import com.example.producer.application.port.out.PaperSessionStorePort;
import com.example.producer.application.port.out.SessionMarketDataSourcePort;
import com.example.producer.domain.strategy.InstrumentHistory;
import com.example.producer.domain.strategy.OpenPosition;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Profile("synthetic-demo")
public class PaperSimulationService {
    private final SessionMarketDataSourcePort marketData;
    private final PaperSessionStorePort sessions;
    private final PaperPortfolioStorePort portfolio;
    private final MarketResearchStorePort research;
    private final AvailableMarketScreeningService screening;

    public PaperSimulationService(SessionMarketDataSourcePort marketData,
                                  PaperSessionStorePort sessions,
                                  PaperPortfolioStorePort portfolio,
                                  MarketResearchStorePort research,
                                  AvailableMarketScreeningService screening) {
        this.marketData = marketData;
        this.sessions = sessions;
        this.portfolio = portfolio;
        this.research = research;
        this.screening = screening;
    }

    public List<LocalDate> availableSessions() {
        return marketData.availableSessions();
    }

    @Transactional
    public SessionResult advance(UUID portfolioId, LocalDate session) {
        List<LocalDate> available = marketData.availableSessions();
        int index = available.indexOf(session);
        if (index < 20) {
            throw new IllegalArgumentException("Choose an available session with 20 earlier sessions");
        }
        LocalDate previous = sessions.lockLastSession(portfolioId);
        if (previous == null && !portfolio.view(portfolioId).trades().isEmpty()) {
            throw new IllegalArgumentException(
                    "This older portfolio has trades without session dates; create a new paper portfolio");
        }
        if (previous != null && !previous.equals(session)) {
            int previousIndex = available.indexOf(previous);
            if (previousIndex < 0 || index != previousIndex + 1) {
                throw new IllegalArgumentException("Portfolio must advance exactly one session at a time");
            }
        }

        MarketDataSnapshot marketSnapshot = marketData.loadSession(session);
        var settled = new ArrayList<PaperSessionStorePort.OrderView>();
        if (previous != null && !previous.equals(session)) {
            for (var order : sessions.pendingOrders(portfolioId)) {
                if (!previous.equals(order.requestedSession())) {
                    throw new IllegalStateException("Pending order belongs to an unexpected session");
                }
                var instrument = marketSnapshot.instruments().stream()
                        .filter(candidate -> candidate.history().instrumentId().equals(order.instrumentId()))
                        .findFirst().orElse(null);
                if (instrument == null || instrument.history().closes().isEmpty()) {
                    sessions.markRejected(order.orderId(), "NO_NEXT_SESSION_PRICE");
                } else {
                    BigDecimal fillPrice = instrument.history().closes()
                            .get(instrument.history().closes().size() - 1).adjustedClose();
                    try {
                        var trade = portfolio.trade(portfolioId,
                                new PaperPortfolioStorePort.TradeCommand(order.clientOrderId(),
                                        order.runId(), order.instrumentId(), order.side(),
                                        order.quantity(), fillPrice, session));
                        sessions.markFilled(order.orderId(), trade.tradeId());
                    } catch (IllegalArgumentException rejection) {
                        sessions.markRejected(order.orderId(), rejection.getMessage());
                    }
                }
                settled.add(sessions.findOrder(portfolioId, order.clientOrderId()));
            }
        }
        sessions.setLastSession(portfolioId, session);

        Map<UUID, PaperSessionStorePort.HeldPosition> holdings = sessions.positions(portfolioId)
                .stream().collect(Collectors.toMap(PaperSessionStorePort.HeldPosition::instrumentId,
                        Function.identity()));
        var positionAware = new MarketDataSnapshot(marketSnapshot.snapshotId(), session, true,
                marketSnapshot.instruments().stream().map(instrument -> {
                    var original = instrument.history();
                    var holding = holdings.get(original.instrumentId());
                    int openedIndex = holding == null || holding.openedSession() == null
                            ? index : available.indexOf(holding.openedSession());
                    OpenPosition position = holding == null ? null : new OpenPosition(
                            Math.max(0, index - openedIndex));
                    return new MarketInstrument(instrument.market(), instrument.symbol(),
                            instrument.currency(), new InstrumentHistory(original.instrumentId(),
                                    original.assetType(), original.closes(), position));
                }).toList());
        AvailableMarketScreeningResult result = screening.screenSnapshot(positionAware);
        UUID runId = research.saveForPortfolio(portfolioId, marketSnapshot, positionAware, result);
        return new SessionResult(runId, result, settled);
    }

    @Transactional
    public PaperSessionStorePort.OrderView submit(UUID portfolioId, UUID clientOrderId,
                                                   UUID runId, UUID instrumentId, String side,
                                                   BigDecimal quantity) {
        new PaperPortfolioStorePort.TradeCommand(clientOrderId, runId, instrumentId, side, quantity);
        LocalDate current = sessions.lockLastSession(portfolioId);
        var replay = sessions.findOrder(portfolioId, clientOrderId);
        if (replay != null) {
            if (!replay.runId().equals(runId) || !replay.instrumentId().equals(instrumentId)
                    || !replay.side().equals(side) || replay.quantity().compareTo(quantity) != 0) {
                throw new IllegalStateException("Client order ID was already used for a different order");
            }
            return replay;
        }
        if (current == null || current.equals(marketData.availableSessions()
                .get(marketData.availableSessions().size() - 1))) {
            throw new IllegalArgumentException("No following session is available for this order");
        }
        var signal = sessions.runSignal(portfolioId, runId, instrumentId);
        if (signal == null || !current.equals(signal.session())
                || !side.equals(signal.action()) || signal.referencePrice() == null) {
            throw new IllegalArgumentException("Order must match a current BUY or SELL signal for this portfolio");
        }
        if (sessions.pendingOrders(portfolioId).stream()
                .anyMatch(order -> order.instrumentId().equals(instrumentId))) {
            throw new IllegalArgumentException("An order for this instrument is already pending");
        }
        if (side.equals("SELL") && sessions.positions(portfolioId).stream()
                .noneMatch(position -> position.instrumentId().equals(instrumentId))) {
            throw new IllegalArgumentException("Portfolio has no position to sell");
        }
        return sessions.insertPending(portfolioId, clientOrderId, runId, instrumentId,
                side, quantity, current);
    }

    public List<PaperSessionStorePort.OrderView> orders(UUID portfolioId) {
        portfolio.view(portfolioId);
        return sessions.orders(portfolioId);
    }

    public record SessionResult(UUID runId, AvailableMarketScreeningResult screening,
                                List<PaperSessionStorePort.OrderView> settledOrders) {
    }
}
