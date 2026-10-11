package com.example.producer.adapter.out.persistence;

import com.example.producer.application.port.out.EtfBenchmarkStorePort;
import com.example.producer.application.port.out.EtfNavSourcePort;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.example.producer.jooq.tables.EtfBenchmarkMarketCloses.ETF_BENCHMARK_MARKET_CLOSES;
import static com.example.producer.jooq.tables.PaperDailyValuations.PAPER_DAILY_VALUATIONS;

@Repository
public class JooqEtfBenchmarkStore implements EtfBenchmarkStorePort {
    private final DSLContext dsl;

    public JooqEtfBenchmarkStore(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public void save(EtfNavSourcePort.Fund fund, List<EtfNavSourcePort.Nav> navs) {
        for (var nav : navs) {
            dsl.insertInto(ETF_BENCHMARK_MARKET_CLOSES)
                    .set(ETF_BENCHMARK_MARKET_CLOSES.FUND_CODE, fund.name())
                    .set(ETF_BENCHMARK_MARKET_CLOSES.SESSION_DATE, nav.session())
                    .set(ETF_BENCHMARK_MARKET_CLOSES.CLOSE_USD, nav.navUsd())
                    .onConflict(ETF_BENCHMARK_MARKET_CLOSES.FUND_CODE, ETF_BENCHMARK_MARKET_CLOSES.SESSION_DATE)
                    .doUpdate().set(ETF_BENCHMARK_MARKET_CLOSES.CLOSE_USD, nav.navUsd())
                    .set(ETF_BENCHMARK_MARKET_CLOSES.IMPORTED_AT, DSL.currentOffsetDateTime())
                    .execute();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean refreshedToday() {
        var today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        for (var fund : EtfNavSourcePort.Fund.values()) {
            var newest = dsl.select(DSL.max(ETF_BENCHMARK_MARKET_CLOSES.IMPORTED_AT))
                    .from(ETF_BENCHMARK_MARKET_CLOSES)
                    .where(ETF_BENCHMARK_MARKET_CLOSES.FUND_CODE.eq(fund.name()))
                    .fetchOne(0, java.time.OffsetDateTime.class);
            if (newest == null || !newest.atZoneSameInstant(java.time.ZoneOffset.UTC).toLocalDate().equals(today)) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Window paperWindow(UUID comparisonId) {
        var row = dsl.select(DSL.min(PAPER_DAILY_VALUATIONS.SESSION_DATE),
                        DSL.max(PAPER_DAILY_VALUATIONS.SESSION_DATE))
                .from(PAPER_DAILY_VALUATIONS)
                .where(PAPER_DAILY_VALUATIONS.COMPARISON_ID.eq(comparisonId))
                .fetchOne();
        return row == null || row.value1() == null ? null : new Window(row.value1(), row.value2());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NavPoint> navs(LocalDate from, LocalDate to) {
        return dsl.selectFrom(ETF_BENCHMARK_MARKET_CLOSES)
                .where(ETF_BENCHMARK_MARKET_CLOSES.SESSION_DATE.between(from, to))
                .orderBy(ETF_BENCHMARK_MARKET_CLOSES.SESSION_DATE.asc())
                .fetch(row -> new NavPoint(EtfNavSourcePort.Fund.valueOf(row.getFundCode()),
                        row.getSessionDate(), row.getCloseUsd()));
    }
}
