package com.example.producer.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/events")
public class EventsController {
    private final EventPublisher publisher;

    public EventsController(EventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping
    public ResponseEntity<?> publish(@RequestBody EventRequest req) {
        String value = "{\"type\":\"" + req.type() + "\",\"payload\":" + req.payload() + "}";
        String id = publisher.publish(value);
        return ResponseEntity.accepted().body(Map.of("id", id));
    }
}
