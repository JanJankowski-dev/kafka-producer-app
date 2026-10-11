package com.example.producer.adapter.out.persistence;

import com.example.producer.application.port.out.MarketDataSnapshot;
import com.example.producer.application.port.out.MarketImportStorePort;
import com.example.producer.application.port.out.HistoricalPricesPort.DailyPrice;
import com.example.producer.application.port.out.MarketInstrument;
import com.example.producer.application.service.MarketDataUnavailableException;
import com.example.producer.domain.market.Sp500Top10Universe;
import com.example.producer.domain.strategy.AssetType;
import com.example.producer.domain.strategy.DailyClose;
import com.example.producer.domain.strategy.InstrumentHistory;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.example.producer.jooq.tables.MarketImportBars.MARKET_IMPORT_BARS;
import static com.example.producer.jooq.tables.MarketImportBatches.MARKET_IMPORT_BATCHES;

@Repository
public class JooqMarketImportStore implements MarketImportStorePort {
    private final DSLContext dsl;

    public JooqMarketImportStore(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public ImportStatus save(String contentHash, LocalDate latestSession, int sessionCount,
                             List<ImportedHistory> histories) {
        UUID newId = UUID.randomUUID();
        dsl.insertInto(MARKET_IMPORT_BATCHES)
                .set(MARKET_IMPORT_BATCHES.BATCH_ID, newId)
                .set(MARKET_IMPORT_BATCHES.SOURCE, "EODHD_EOD")
                .set(MARKET_IMPORT_BATCHES.UNIVERSE_VERSION, Sp500Top10Universe.VERSION)
                .set(MARKET_IMPORT_BATCHES.UNIVERSE_AS_OF, Sp500Top10Universe.AS_OF)
                .set(MARKET_IMPORT_BATCHES.LATEST_COMMON_SESSION, latestSession)
                .set(MARKET_IMPORT_BATCHES.CONTENT_HASH, contentHash)
                .set(MARKET_IMPORT_BATCHES.INSTRUMENT_COUNT, histories.size())
                .set(MARKET_IMPORT_BATCHES.SESSION_COUNT, sessionCount)
                .onConflict(MARKET_IMPORT_BATCHES.CONTENT_HASH).doUpdate()
                .set(MARKET_IMPORT_BATCHES.IMPORTED_AT, org.jooq.impl.DSL.currentOffsetDateTime())
                .execute();
        var batch = dsl.selectFrom(MARKET_IMPORT_BATCHES)
                .where(MARKET_IMPORT_BATCHES.CONTENT_HASH.eq(contentHash)).fetchOne();
        if (batch == null) {
            throw new IllegalStateException("Market import batch disappeared");
        }
        if (!dsl.fetchExists(dsl.selectOne().from(MARKET_IMPORT_BARS)
                .where(MARKET_IMPORT_BARS.BATCH_ID.eq(batch.getBatchId())))) {
            for (var history : histories) {
                for (var price : history.prices()) {
                    dsl.insertInto(MARKET_IMPORT_BARS)
                            .set(MARKET_IMPORT_BARS.BATCH_ID, batch.getBatchId())
                            .set(MARKET_IMPORT_BARS.INSTRUMENT_ID, history.instrument().instrumentId())
                            .set(MARKET_IMPORT_BARS.SYMBOL, history.instrument().symbol())
                            .set(MARKET_IMPORT_BARS.SESSION_DATE, price.sessionDate())
                            .set(MARKET_IMPORT_BARS.RAW_CLOSE, price.rawClose())
                            .set(MARKET_IMPORT_BARS.RAW_OPEN, price.rawOpen())
                            .set(MARKET_IMPORT_BARS.ADJUSTED_CLOSE, price.adjustedClose())
                            .set(MARKET_IMPORT_BARS.VOLUME, price.volume())
                            .set(MARKET_IMPORT_BARS.SPLIT_FACTOR, price.splitFactor())
                            .set(MARKET_IMPORT_BARS.DIVIDEND_CASH, price.dividendCash())
                            .execute();
                }
            }
        }
        return status(batch);
    }

    @Override
    @Transactional(readOnly = true)
    public ImportStatus latestStatus() {
        var batch = dsl.selectFrom(MARKET_IMPORT_BATCHES)
                .where(MARKET_IMPORT_BATCHES.SOURCE.eq("EODHD_EOD"))
                .orderBy(MARKET_IMPORT_BATCHES.IMPORTED_AT.desc(), MARKET_IMPORT_BATCHES.BATCH_ID.desc())
                .limit(1).fetchOne();
        return batch == null ? null : status(batch);
    }

    @Override
    @Transactional(readOnly = true)
    public MarketDataSnapshot latestSnapshot() {
        ImportStatus latest = latestStatus();
        if (latest == null) {
            throw new MarketDataUnavailableException("No real market prices have been imported yet");
        }
        var bars = dsl.selectFrom(MARKET_IMPORT_BARS)
                .where(MARKET_IMPORT_BARS.BATCH_ID.eq(latest.batchId()))
                .orderBy(MARKET_IMPORT_BARS.SESSION_DATE)
                .fetch();
        var instruments = Sp500Top10Universe.instruments().stream().map(definition -> {
            List<DailyClose> closes = bars.stream()
                    .filter(row -> row.getInstrumentId().equals(definition.instrumentId()))
                    .map(row -> new DailyClose(row.getSessionDate(), row.getAdjustedClose()))
                    .toList();
            if (closes.size() < 21 || !closes.get(closes.size() - 1).sessionDate()
                    .equals(latest.latestCommonSession())) {
                throw new MarketDataUnavailableException("Incomplete imported prices for "
                        + definition.symbol());
            }
            closes = closes.subList(closes.size() - 21, closes.size());
            return new MarketInstrument(definition.market(), definition.symbol(), definition.currency(),
                    new InstrumentHistory(definition.instrumentId(), AssetType.STOCK, closes, null));
        }).toList();
        return new MarketDataSnapshot(latest.source().toLowerCase(java.util.Locale.ROOT) + "-"
                + latest.batchId() + "-" + latest.latestCommonSession(),
                latest.latestCommonSession(), false, instruments);
    }

    @Override
    @Transactional(readOnly = true)
    public ImportedBatch latestHistories() {
        ImportStatus latest = latestStatus();
        if (latest == null) {
            throw new MarketDataUnavailableException("No real market prices have been imported yet");
        }
        var bars = dsl.selectFrom(MARKET_IMPORT_BARS)
                .where(MARKET_IMPORT_BARS.BATCH_ID.eq(latest.batchId()))
                .orderBy(MARKET_IMPORT_BARS.SESSION_DATE)
                .fetch();
        var histories = Sp500Top10Universe.instruments().stream().map(definition -> {
            List<DailyPrice> prices = bars.stream()
                    .filter(row -> row.getInstrumentId().equals(definition.instrumentId()))
                    .map(row -> row.getRawOpen() == null
                            ? new DailyPrice(row.getSessionDate(), row.getRawClose(),
                                    row.getAdjustedClose(), row.getVolume())
                            : new DailyPrice(row.getSessionDate(), row.getRawOpen(), row.getRawClose(),
                                    row.getAdjustedClose(), row.getVolume(), row.getSplitFactor(),
                                    row.getDividendCash()))
                    .toList();
            if (prices.size() != latest.sessionCount() || !prices.get(prices.size() - 1)
                    .sessionDate().equals(latest.latestCommonSession())) {
                throw new MarketDataUnavailableException("Incomplete imported history for "
                        + definition.symbol());
            }
            return new ImportedHistory(definition, prices);
        }).toList();
        return new ImportedBatch(latest, histories);
    }

    private ImportStatus status(com.example.producer.jooq.tables.records.MarketImportBatchesRecord batch) {
        return new ImportStatus(batch.getBatchId(), batch.getSource(), batch.getUniverseVersion(),
                batch.getUniverseAsOf(), batch.getLatestCommonSession(), batch.getImportedAt(),
                batch.getInstrumentCount(), batch.getSessionCount());
    }
}
