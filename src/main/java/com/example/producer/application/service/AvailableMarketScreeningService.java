package com.example.producer.application.service;

import com.example.producer.application.port.in.AvailableMarketScreeningResult;
import com.example.producer.application.port.in.ScreenAvailableMarketUseCase;
import com.example.producer.application.port.in.ScreenMarketCommand;
import com.example.producer.application.port.in.ScreenMarketUseCase;
import com.example.producer.application.port.out.MarketDataSourcePort;
import com.example.producer.application.port.out.MarketInstrument;
import com.example.producer.application.port.out.MarketDataSnapshot;

import java.util.function.Function;
import java.util.stream.Collectors;

public class AvailableMarketScreeningService implements ScreenAvailableMarketUseCase {
    private final MarketDataSourcePort marketData;
    private final ScreenMarketUseCase screening;

    public AvailableMarketScreeningService(MarketDataSourcePort marketData, ScreenMarketUseCase screening) {
        this.marketData = marketData;
        this.screening = screening;
    }

    @Override
    public AvailableMarketScreeningResult screenAvailableMarket() {
        return screenSnapshot(marketData.loadSnapshot());
    }

    public AvailableMarketScreeningResult screenSnapshot(MarketDataSnapshot snapshot) {
        var metadata = snapshot.instruments().stream()
                .collect(Collectors.toMap(instrument -> instrument.history().instrumentId(),
                        Function.identity()));
        var result = screening.screen(new ScreenMarketCommand(snapshot.asOfSession(),
                snapshot.instruments().stream().map(MarketInstrument::history).toList()));
        var instruments = result.signals().stream().map(signal -> {
            var instrument = metadata.get(signal.instrumentId());
            return new AvailableMarketScreeningResult.ScreenedInstrument(
                    instrument.market(), instrument.symbol(), instrument.currency(), signal);
        }).toList();
        return new AvailableMarketScreeningResult(
                snapshot.snapshotId(), snapshot.asOfSession(), snapshot.synthetic(), instruments);
    }
}
