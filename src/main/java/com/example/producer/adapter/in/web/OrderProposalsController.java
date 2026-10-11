package com.example.producer.adapter.in.web;

import com.example.producer.application.port.in.OrderProposalAccepted;
import com.example.producer.application.port.in.ProposeOrderUseCase;
import com.example.producer.application.service.IdempotencyConflictException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order-proposals")
@Slf4j
public class OrderProposalsController {
    private final ProposeOrderUseCase useCase;

    public OrderProposalsController(ProposeOrderUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<?> propose(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderProposalRequest request
    ) {
        try {
            OrderProposalAccepted accepted = useCase.propose(idempotencyKey, request.toCommand());
            return ResponseEntity.accepted().body(
                    new OrderProposalAcceptedResponse(
                            accepted.eventId(),
                            accepted.proposalId()
                    )
            );
        } catch (IdempotencyConflictException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    new ApiErrorResponse("IDEMPOTENCY_KEY_CONFLICT", exception.getMessage())
            );
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(
                    new ApiErrorResponse("INVALID_ORDER_PROPOSAL", exception.getMessage())
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Order proposal store failed portfolioId={} instrumentId={}",
                    request.portfolioId(),
                    request.instrumentId(),
                    exception
            );
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    new ApiErrorResponse(
                            "ORDER_PROPOSAL_UNAVAILABLE",
                            "Order proposal could not be accepted"
                    )
            );
        }
    }
}
