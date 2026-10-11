package com.example.producer.application.service;

import com.example.producer.application.port.out.EtfBenchmarkStorePort;
import com.example.producer.application.port.out.EtfNavSourcePort;
import com.example.producer.application.port.out.StrategyComparisonStorePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

@Service
public class EtfBenchmarkService {
    private static final BigDecimal STARTING_USD = new BigDecimal("10000.00");
    private final EtfNavSourcePort source;
    private final EtfBenchmarkStorePort store;
    private final StrategyComparisonStorePort comparisons;

    public EtfBenchmarkService(EtfNavSourcePort source, EtfBenchmarkStorePort store,
                               StrategyComparisonStorePort comparisons) {
        this.source = source;
        this.store = store;
        this.comparisons = comparisons;
    }

    public int refresh() {
        var comparison = comparisons.latest();
        if (comparison == null) throw new MarketDataUnavailableException("Create paper portfolios first");
        var window = store.paperWindow(comparison.comparisonId());
        if (window == null) throw new MarketDataUnavailableException("Run the first daily valuation first");
        if (store.refreshedToday()) return 0;
        LocalDate retrospectiveStart = window.firstPaperSession().minusDays(7);
        // Download both before writing either, so a partial provider outage does not create half a comparison.
        List<EtfNavSourcePort.Nav> cspx;
        List<EtfNavSourcePort.Nav> iwda;
        try {
            cspx = source.download(EtfNavSourcePort.Fund.CSPX).stream()
                    .filter(nav -> !nav.session().isBefore(retrospectiveStart)).toList();
            iwda = source.download(EtfNavSourcePort.Fund.IWDA).stream()
                    .filter(nav -> !nav.session().isBefore(retrospectiveStart)).toList();
        } catch (RuntimeException exception) {
            throw new MarketDataUnavailableException("Ceny ETF z EODHD są teraz niedostępne", exception);
        }
        if (cspx.isEmpty() || iwda.isEmpty()) {
            throw new MarketDataUnavailableException("No EODHD ETF closes in the retrospective comparison window");
        }
        store.save(EtfNavSourcePort.Fund.CSPX, cspx);
        store.save(EtfNavSourcePort.Fund.IWDA, iwda);
        return cspx.size() + iwda.size();
    }

    public BenchmarkView latest() {
        var comparison = comparisons.latest();
        if (comparison == null) return new BenchmarkView(null, null, null, List.of(),
                "EODHD_MARKET_CLOSE_USD", "Brak portfeli paper");
        var window = store.paperWindow(comparison.comparisonId());
        if (window == null) return new BenchmarkView(null, null, null, List.of(),
                "EODHD_MARKET_CLOSE_USD", "Brak pierwszej wyceny paper");
        List<EtfBenchmarkStorePort.NavPoint> data = store.navs(window.firstPaperSession().minusDays(7),
                window.latestPaperSession());
        var byFund = new EnumMap<EtfNavSourcePort.Fund, List<EtfBenchmarkStorePort.NavPoint>>(
                EtfNavSourcePort.Fund.class);
        for (var fund : EtfNavSourcePort.Fund.values()) byFund.put(fund, new ArrayList<>());
        for (var nav : data) byFund.get(nav.fund()).add(nav);
        // Both comparators start on one shared, actually observed market session.
        LocalDate commonStart = byFund.get(EtfNavSourcePort.Fund.CSPX).stream()
                .map(EtfBenchmarkStorePort.NavPoint::session)
                .filter(date -> byFund.get(EtfNavSourcePort.Fund.IWDA).stream()
                        .anyMatch(other -> other.session().equals(date)))
                .findFirst().orElse(null);
        if (commonStart == null) return new BenchmarkView(null, window.firstPaperSession(),
                window.latestPaperSession(), List.of(), "EODHD_MARKET_CLOSE_USD", "Brak wspólnej daty cen ETF; odśwież dane");
        List<BenchmarkPoint> points = new ArrayList<>();
        for (var fund : EtfNavSourcePort.Fund.values()) {
            var navs = byFund.get(fund).stream().filter(nav -> !nav.session().isBefore(commonStart)).toList();
            BigDecimal initial = navs.get(0).navUsd();
            for (var nav : navs) {
                BigDecimal equity = STARTING_USD.multiply(nav.navUsd())
                        .divide(initial, 2, RoundingMode.HALF_UP);
                points.add(new BenchmarkPoint(fund.name(), fund.isin(), fund.label(),
                        nav.session(), nav.navUsd(), equity));
            }
        }
        return new BenchmarkView(commonStart, window.firstPaperSession(), window.latestPaperSession(), points,
                "EODHD_MARKET_CLOSE_USD", "Tydzień przed startem paper to retrospekcja ETF, nie zapisane wtedy decyzje reguł lub AI");
    }

    public record BenchmarkView(LocalDate commonStart, LocalDate paperStart, LocalDate latestPaperSession,
                                List<BenchmarkPoint> points, String source, String note) { }
    public record BenchmarkPoint(String fund, String isin, String label,
                                 LocalDate session, BigDecimal navUsd, BigDecimal equityUsd) { }
}
