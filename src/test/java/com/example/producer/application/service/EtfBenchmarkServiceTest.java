package com.example.producer.application.service;

import com.example.producer.application.port.out.EtfBenchmarkStorePort;
import com.example.producer.application.port.out.EtfNavSourcePort;
import com.example.producer.application.port.out.StrategyComparisonStorePort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EtfBenchmarkServiceTest {
    @Test
    void usesRealSharedNavStartAndNormalizesBuyAndHold() {
        var source = mock(EtfNavSourcePort.class);
        var store = mock(EtfBenchmarkStorePort.class);
        var comparisons = mock(StrategyComparisonStorePort.class);
        UUID id = UUID.randomUUID();
        when(comparisons.latest()).thenReturn(new StrategyComparisonStorePort.ComparisonView(
                id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "BREAKOUT_20_V1", null, null));
        LocalDate first = LocalDate.of(2026, 10, 2);
        LocalDate second = LocalDate.of(2026, 10, 5);
        LocalDate retrospective = first.minusDays(7);
        when(store.paperWindow(id)).thenReturn(new EtfBenchmarkStorePort.Window(first, second));
        when(source.download(EtfNavSourcePort.Fund.CSPX)).thenReturn(List.of(
                new EtfNavSourcePort.Nav(retrospective, new BigDecimal("790")),
                new EtfNavSourcePort.Nav(first, new BigDecimal("800")),
                new EtfNavSourcePort.Nav(second, new BigDecimal("808"))));
        when(source.download(EtfNavSourcePort.Fund.IWDA)).thenReturn(List.of(
                new EtfNavSourcePort.Nav(retrospective, new BigDecimal("139")),
                new EtfNavSourcePort.Nav(first, new BigDecimal("140")),
                new EtfNavSourcePort.Nav(second, new BigDecimal("138.60"))));
        when(store.navs(retrospective, second)).thenReturn(List.of(
                new EtfBenchmarkStorePort.NavPoint(EtfNavSourcePort.Fund.CSPX, retrospective, new BigDecimal("790")),
                new EtfBenchmarkStorePort.NavPoint(EtfNavSourcePort.Fund.IWDA, retrospective, new BigDecimal("139")),
                new EtfBenchmarkStorePort.NavPoint(EtfNavSourcePort.Fund.CSPX, first, new BigDecimal("800")),
                new EtfBenchmarkStorePort.NavPoint(EtfNavSourcePort.Fund.IWDA, first, new BigDecimal("140")),
                new EtfBenchmarkStorePort.NavPoint(EtfNavSourcePort.Fund.CSPX, second, new BigDecimal("808")),
                new EtfBenchmarkStorePort.NavPoint(EtfNavSourcePort.Fund.IWDA, second, new BigDecimal("138.60"))));

        var service = new EtfBenchmarkService(source, store, comparisons);
        assertThat(service.refresh()).isEqualTo(6);
        verify(store, times(2)).save(any(), any());

        var result = service.latest();
        assertThat(result.commonStart()).isEqualTo(retrospective);
        assertThat(result.paperStart()).isEqualTo(first);
        assertThat(result.points()).hasSize(6);
        assertThat(result.points().stream().filter(p -> p.fund().equals("CSPX") && p.session().equals(second))
                .findFirst().orElseThrow().equityUsd()).isEqualByComparingTo("10227.85");
        assertThat(result.points().stream().filter(p -> p.fund().equals("IWDA") && p.session().equals(second))
                .findFirst().orElseThrow().equityUsd()).isEqualByComparingTo("9971.22");
    }
}
