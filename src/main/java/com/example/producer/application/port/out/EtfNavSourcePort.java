package com.example.producer.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface EtfNavSourcePort {
    List<Nav> download(Fund fund);

    enum Fund {
        CSPX("IE00B5BMR087", "CSSPX.SW", "S&P 500 ETF Acc"),
        IWDA("IE00B4L5Y983", "SWDA.SW", "MSCI World ETF Acc");

        private final String isin;
        private final String eodhdTicker;
        private final String label;

        Fund(String isin, String eodhdTicker, String label) {
            this.isin = isin;
            this.eodhdTicker = eodhdTicker;
            this.label = label;
        }

        public String isin() { return isin; }
        public String eodhdTicker() { return eodhdTicker; }
        public String label() { return label; }
    }

    record Nav(LocalDate session, BigDecimal navUsd) { }
}
