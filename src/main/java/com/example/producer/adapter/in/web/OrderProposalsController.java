package com.example.producer.adapter.in.web;

import com.example.producer.application.port.in.OrderProposalAccepted;
import com.example.producer.application.port.in.ProposeOrderUseCase;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@RestController
@RequestMapping("/order-proposals")
@Slf4j
public class OrderProposalsController {
    private final ProposeOrderUseCase useCase;

    public OrderProposalsController(ProposeOrderUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<?>> propose(
            @Valid @RequestBody CreateOrderProposalRequest request
    ) {
        try {
            return handleResult(
                    request,
                    useCase.propose(request.toCommand()).toCompletableFuture()
            );
        } catch (IllegalArgumentException exception) {
            ResponseEntity<?> response = ResponseEntity.badRequest().body(
                    new ApiErrorResponse("INVALID_ORDER_PROPOSAL", exception.getMessage())
            );
            return CompletableFuture.completedFuture(response);
        }
    }

    private CompletableFuture<ResponseEntity<?>> handleResult(
            CreateOrderProposalRequest request,
            CompletableFuture<OrderProposalAccepted> result
    ) {
        return result
                .handle((accepted, error) -> {
                    if (error == null) {
                        return ResponseEntity.accepted().body(
                                new OrderProposalAcceptedResponse(
                                        accepted.eventId(),
                                        accepted.proposalId()
                                )
                        );
                    }

                    Throwable cause = unwrap(error);
                    if (cause instanceof IllegalArgumentException) {
                        return ResponseEntity.badRequest().body(
                                new ApiErrorResponse("INVALID_ORDER_PROPOSAL", cause.getMessage())
                        );
                    }

                    log.error(
                            "Order proposal publish failed portfolioId={} instrumentId={}",
                            request.portfolioId(),
                            request.instrumentId(),
                            cause
                    );
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                            new ApiErrorResponse(
                                    "ORDER_PROPOSAL_UNAVAILABLE",
                                    "Order proposal could not be published"
                            )
                    );
                });
    }

    private Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null
                ? error.getCause()
                : error;
    }
}
