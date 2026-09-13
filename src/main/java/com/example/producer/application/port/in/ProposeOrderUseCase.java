package com.example.producer.application.port.in;

import java.util.concurrent.CompletionStage;

public interface ProposeOrderUseCase {
    CompletionStage<OrderProposalAccepted> propose(ProposeOrderCommand command);
}
