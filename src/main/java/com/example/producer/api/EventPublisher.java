package com.example.producer.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                          @Value("${app.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public String publish(String value) {
        String key = UUID.randomUUID().toString();
        kafkaTemplate.send(topic, key, value);
        return key;
    }
}
