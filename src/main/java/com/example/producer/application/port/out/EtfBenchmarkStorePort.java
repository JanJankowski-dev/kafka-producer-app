package com.example.producer.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EtfBenchmarkStorePort {
    void save(EtfNavSourcePort.Fund fund, List<EtfNavSourcePort.Nav> navs);
    boolean refreshedToday();
    Window paperWindow(UUID comparisonId);
    List<NavPoint> navs(LocalDate from, LocalDate to);

    record Window(LocalDate firstPaperSession, LocalDate latestPaperSession) { }
    record NavPoint(EtfNavSourcePort.Fund fund, LocalDate session, BigDecimal navUsd) { }
}
