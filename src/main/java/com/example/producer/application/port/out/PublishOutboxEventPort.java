package com.example.producer.application.port.out;

import java.util.concurrent.CompletionStage;

public interface PublishOutboxEventPort {
    CompletionStage<Void> publish(PendingOutboxEvent event);
}
