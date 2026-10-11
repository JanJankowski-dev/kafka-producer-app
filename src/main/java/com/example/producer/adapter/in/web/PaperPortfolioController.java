package com.example.producer.adapter.in.web;

import com.example.producer.application.service.PaperPortfolioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/paper-portfolios")
public class PaperPortfolioController {
    private final PaperPortfolioService service;

    public PaperPortfolioController(PaperPortfolioService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, UUID> create() {
        return Map.of("portfolioId", service.create());
    }

    @GetMapping("/{portfolioId}")
    public ResponseEntity<?> view(@PathVariable UUID portfolioId) {
        try {
            return ResponseEntity.ok(service.view(portfolioId));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
    }

}
