package com.example.producer.adapter.out.persistence;

import com.example.producer.application.port.in.AvailableMarketScreeningResult;
import com.example.producer.application.port.out.MarketDataSnapshot;
import com.example.producer.application.port.out.MarketResearchStorePort;
import com.example.producer.domain.strategy.Breakout20Strategy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import static com.example.producer.jooq.tables.MarketDailyCloses.MARKET_DAILY_CLOSES;
import static com.example.producer.jooq.tables.MarketInstruments.MARKET_INSTRUMENTS;
import static com.example.producer.jooq.tables.MarketSnapshots.MARKET_SNAPSHOTS;
import static com.example.producer.jooq.tables.ScreeningRuns.SCREENING_RUNS;
import static com.example.producer.jooq.tables.ScreeningSignals.SCREENING_SIGNALS;

@Repository
public class JooqMarketResearchStore implements MarketResearchStorePort {
    private final DSLContext dsl;
    private final ObjectMapper mapper;

    public JooqMarketResearchStore(DSLContext dsl, ObjectMapper mapper) {
        this.dsl = dsl;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public UUID save(MarketDataSnapshot snapshot, AvailableMarketScreeningResult result) {
        return saveInternal(null, snapshot, null, result);
    }

    @Override
    @Transactional
    public UUID saveForPortfolio(UUID portfolioId, MarketDataSnapshot marketSnapshot,
                                 MarketDataSnapshot positionAwareSnapshot,
                                 AvailableMarketScreeningResult result) {
        return saveInternal(portfolioId, marketSnapshot, hash(positionAwareSnapshot), result);
    }

    private UUID saveInternal(UUID portfolioId, MarketDataSnapshot snapshot, String positionStateHash,
                              AvailableMarketScreeningResult result) {
        String hash = hash(snapshot);
        int inserted = dsl.insertInto(MARKET_SNAPSHOTS)
                .set(MARKET_SNAPSHOTS.SNAPSHOT_ID, snapshot.snapshotId())
                .set(MARKET_SNAPSHOTS.AS_OF_SESSION, snapshot.asOfSession())
                .set(MARKET_SNAPSHOTS.SYNTHETIC, snapshot.synthetic())
                .set(MARKET_SNAPSHOTS.CONTENT_HASH, hash)
                .onConflict(MARKET_SNAPSHOTS.SNAPSHOT_ID).doNothing().execute();
        if (inserted == 0) {
            String existingHash = dsl.select(MARKET_SNAPSHOTS.CONTENT_HASH)
                    .from(MARKET_SNAPSHOTS)
                    .where(MARKET_SNAPSHOTS.SNAPSHOT_ID.eq(snapshot.snapshotId()))
                    .fetchOne(MARKET_SNAPSHOTS.CONTENT_HASH);
            if (!hash.equals(existingHash)) {
                throw new IllegalStateException("Snapshot ID reused for different market data");
            }
        } else {
            for (var instrument : snapshot.instruments()) {
                var history = instrument.history();
                dsl.insertInto(MARKET_INSTRUMENTS)
                        .set(MARKET_INSTRUMENTS.SNAPSHOT_ID, snapshot.snapshotId())
                        .set(MARKET_INSTRUMENTS.INSTRUMENT_ID, history.instrumentId())
                        .set(MARKET_INSTRUMENTS.MARKET, instrument.market())
                        .set(MARKET_INSTRUMENTS.SYMBOL, instrument.symbol())
                        .set(MARKET_INSTRUMENTS.CURRENCY, instrument.currency())
                        .set(MARKET_INSTRUMENTS.ASSET_TYPE, history.assetType().name())
                        .set(MARKET_INSTRUMENTS.HELD_SESSIONS,
                                history.openPosition() == null ? null : history.openPosition().heldSessions())
                        .execute();
                for (var close : history.closes()) {
                    dsl.insertInto(MARKET_DAILY_CLOSES)
                            .set(MARKET_DAILY_CLOSES.SNAPSHOT_ID, snapshot.snapshotId())
                            .set(MARKET_DAILY_CLOSES.INSTRUMENT_ID, history.instrumentId())
                            .set(MARKET_DAILY_CLOSES.SESSION_DATE, close.sessionDate())
                            .set(MARKET_DAILY_CLOSES.ADJUSTED_CLOSE, close.adjustedClose())
                            .execute();
                }
            }
        }

        UUID runId = UUID.randomUUID();
        int runInserted = dsl.insertInto(SCREENING_RUNS)
                .set(SCREENING_RUNS.RUN_ID, runId)
                .set(SCREENING_RUNS.SNAPSHOT_ID, snapshot.snapshotId())
                .set(SCREENING_RUNS.STRATEGY_VERSION, Breakout20Strategy.VERSION)
                .set(SCREENING_RUNS.PORTFOLIO_ID, portfolioId)
                .set(SCREENING_RUNS.POSITION_STATE_HASH, positionStateHash)
                .onConflictDoNothing().execute();
        if (runInserted == 0) {
            var existing = dsl.select(SCREENING_RUNS.RUN_ID, SCREENING_RUNS.POSITION_STATE_HASH)
                    .from(SCREENING_RUNS)
                    .where(SCREENING_RUNS.SNAPSHOT_ID.eq(snapshot.snapshotId()))
                    .and(SCREENING_RUNS.STRATEGY_VERSION.eq(Breakout20Strategy.VERSION))
                    .and(portfolioId == null ? SCREENING_RUNS.PORTFOLIO_ID.isNull()
                            : SCREENING_RUNS.PORTFOLIO_ID.eq(portfolioId))
                    .fetchOne();
            if (existing == null || !java.util.Objects.equals(positionStateHash,
                    existing.get(SCREENING_RUNS.POSITION_STATE_HASH))) {
                throw new IllegalStateException("Screening run ID reused for a different portfolio state");
            }
            return existing.get(SCREENING_RUNS.RUN_ID);
        }
        for (var item : result.instruments()) {
            var signal = item.signal();
            dsl.insertInto(SCREENING_SIGNALS)
                    .set(SCREENING_SIGNALS.RUN_ID, runId)
                    .set(SCREENING_SIGNALS.INSTRUMENT_ID, signal.instrumentId())
                    .set(SCREENING_SIGNALS.ACTION, signal.action().name())
                    .set(SCREENING_SIGNALS.REASON_CODE, signal.reasonCode())
                    .set(SCREENING_SIGNALS.LATEST_ADJUSTED_CLOSE, signal.latestAdjustedClose())
                    .set(SCREENING_SIGNALS.RETURN_OVER_20_SESSIONS_PCT, signal.returnOver20SessionsPct())
                    .set(SCREENING_SIGNALS.MAX_HOLDING_SESSIONS, signal.maxHoldingSessions())
                    .execute();
        }
        return runId;
    }

    private String hash(MarketDataSnapshot snapshot) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(snapshot)));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("Cannot hash market snapshot", exception);
        }
    }
}
