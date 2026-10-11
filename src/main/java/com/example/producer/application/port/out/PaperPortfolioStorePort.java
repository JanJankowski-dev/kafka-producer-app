package com.example.producer.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PaperPortfolioStorePort {
    UUID create();
    PortfolioView view(UUID portfolioId);
    TradeView trade(UUID portfolioId, TradeCommand command);

    record TradeCommand(UUID clientOrderId, UUID runId, UUID instrumentId,
                        String side, BigDecimal quantity, BigDecimal executionPrice,
                        LocalDate executionSession) {
        public TradeCommand(UUID clientOrderId, UUID runId, UUID instrumentId,
                            String side, BigDecimal quantity) {
            this(clientOrderId, runId, instrumentId, side, quantity, null, null);
        }

        public TradeCommand {
            if (clientOrderId == null || runId == null || instrumentId == null
                    || side == null || quantity == null) {
                throw new IllegalArgumentException("All paper trade fields are required");
            }
            if (!side.equals("BUY") && !side.equals("SELL")) {
                throw new IllegalArgumentException("Side must be BUY or SELL");
            }
            if (quantity.signum() <= 0 || quantity.scale() > 8
                    || quantity.precision() - quantity.scale() > 16) {
                throw new IllegalArgumentException("Quantity must be positive with at most 8 decimals");
            }
            if ((executionPrice == null) != (executionSession == null)
                    || (executionPrice != null && executionPrice.signum() <= 0)) {
                throw new IllegalArgumentException("Execution price and session must be provided together");
            }
        }
    }

    record CashView(String currency, BigDecimal balance) {
    }

    record PositionView(UUID instrumentId, String symbol, String currency,
                        BigDecimal quantity, BigDecimal averagePrice) {
    }

    record TradeView(UUID tradeId, UUID clientOrderId, UUID runId, UUID instrumentId,
                     String side, BigDecimal quantity, BigDecimal price, String currency,
                     LocalDate executionSession) {
    }

    record PortfolioView(UUID portfolioId, LocalDate lastSession, List<CashView> cash,
                         List<PositionView> positions, List<TradeView> trades) {
    }
}
