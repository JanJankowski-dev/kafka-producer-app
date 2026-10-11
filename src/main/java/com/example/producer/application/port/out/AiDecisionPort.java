package com.example.producer.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface AiDecisionPort {
    String modelVersion();
    boolean configured();
    List<Decision> decide(LocalDate session, List<Candidate> candidates);

    record Candidate(String symbol, BigDecimal close, BigDecimal return20Pct,
                     boolean held, int heldSessions) { }
    record Decision(String symbol, String action, String reason) { }
}
