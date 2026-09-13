package com.example.producer.application.port.out;

import com.example.producer.domain.OrderProposalProposed;

import java.util.concurrent.CompletionStage;

public interface PublishOrderProposalPort {
    CompletionStage<Void> publish(OrderProposalProposed event);
}
