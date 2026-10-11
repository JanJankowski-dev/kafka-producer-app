package com.example.producer.application.port.in;

public interface ProposeOrderUseCase {
    OrderProposalAccepted propose(String idempotencyKey, ProposeOrderCommand command);
}
