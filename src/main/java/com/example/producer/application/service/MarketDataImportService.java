package com.example.producer.application.service;

import com.example.producer.application.port.out.HistoricalPricesPort;
import com.example.producer.application.port.out.HistoricalPricesPort.DailyPrice;
import com.example.producer.application.port.out.MarketImportStorePort;
import com.example.producer.domain.market.Sp500Top10Universe;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MarketDataImportService {
    private final HistoricalPricesPort provider;
    private final MarketImportStorePort store;

    public MarketDataImportService(HistoricalPricesPort provider, MarketImportStorePort store) {
        this.provider = provider;
        this.store = store;
    }

    public boolean configured() {
        return provider.configured();
    }

    public MarketImportStorePort.ImportStatus latestStatus() {
        return store.latestStatus();
    }

    public MarketImportStorePort.ImportStatus importLatest() {
        if (!provider.configured()) {
            throw new MarketDataUnavailableException("Set EODHD_API_KEY in the local demo environment");
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var previous = store.latestStatus();
        if (previous != null && "EODHD_EOD".equals(previous.source())
                && previous.importedAt().atZoneSameInstant(ZoneOffset.UTC).toLocalDate().equals(today)) {
            return previous;
        }
        var fetched = new ArrayList<MarketImportStorePort.ImportedHistory>();
        Set<LocalDate> common = null;
        for (var instrument : Sp500Top10Universe.instruments()) {
            List<DailyPrice> prices = provider.fetch(instrument.symbol(), today.minusMonths(8), today);
            if (prices.size() < 21) {
                throw new MarketDataUnavailableException("Too few daily prices for " + instrument.symbol());
            }
            var dates = new HashSet<LocalDate>();
            for (DailyPrice price : prices) {
                if (price.sessionDate().isAfter(today) || !dates.add(price.sessionDate())) {
                    throw new MarketDataUnavailableException("Invalid or duplicate session for "
                            + instrument.symbol());
                }
            }
            common = common == null ? dates : common.stream()
                    .filter(dates::contains).collect(Collectors.toSet());
            fetched.add(new MarketImportStorePort.ImportedHistory(instrument, prices));
        }
        if (common == null || common.size() < 21) {
            throw new MarketDataUnavailableException("The 10 symbols have fewer than 21 common sessions");
        }
        List<LocalDate> sessions = common.stream().sorted().toList();
        sessions = sessions.subList(Math.max(0, sessions.size() - 80), sessions.size());
        Set<LocalDate> selected = Set.copyOf(sessions);
        var histories = fetched.stream().map(history ->
                new MarketImportStorePort.ImportedHistory(history.instrument(),
                        history.prices().stream().filter(price -> selected.contains(price.sessionDate()))
                                .sorted(java.util.Comparator.comparing(DailyPrice::sessionDate)).toList()))
                .toList();
        return store.save(hash(histories), sessions.get(sessions.size() - 1), sessions.size(), histories);
    }

    private String hash(List<MarketImportStorePort.ImportedHistory> histories) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update("EODHD_EOD".getBytes(StandardCharsets.UTF_8));
            digest.update(Sp500Top10Universe.VERSION.getBytes(StandardCharsets.UTF_8));
            for (var history : histories) {
                digest.update(history.instrument().symbol().getBytes(StandardCharsets.UTF_8));
                for (var price : history.prices()) {
                    String row = price.sessionDate() + ":" + price.rawOpen().stripTrailingZeros()
                            + ":" + price.rawClose().stripTrailingZeros()
                            + ":" + price.adjustedClose().stripTrailingZeros() + ":" + price.volume()
                            + ":" + price.splitFactor().stripTrailingZeros()
                            + ":" + price.dividendCash().stripTrailingZeros() + ";";
                    digest.update(row.getBytes(StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
