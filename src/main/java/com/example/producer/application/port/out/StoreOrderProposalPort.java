package com.example.producer.application.port.out;

import com.example.producer.domain.OrderProposalProposed;

public interface StoreOrderProposalPort {
    void store(OrderProposalProposed event);
}
