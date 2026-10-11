package com.example.producer.adapter.in.web;

import com.example.producer.application.service.AutoPaperTradingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/private/paper-activity")
public class PrivatePaperActivityController {
    private final AutoPaperTradingService service;
    private final boolean enabled;

    public PrivatePaperActivityController(AutoPaperTradingService service,
                                          @Value("${app.private-http.enabled:false}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @GetMapping
    public ResponseEntity<?> latest() {
        return enabled ? ResponseEntity.ok(service.latestActivity()) : ResponseEntity.notFound().build();
    }
}
