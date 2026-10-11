package com.example.producer.application.port.out;

import com.example.producer.application.port.in.OrderProposalAccepted;
import com.example.producer.domain.OrderProposalProposed;

import java.util.function.Supplier;

public interface AcceptOrderProposalPort {
    OrderProposalAccepted accept(
            String idempotencyKey,
            String requestFingerprint,
            Supplier<OrderProposalProposed> newEvent
    );
}
