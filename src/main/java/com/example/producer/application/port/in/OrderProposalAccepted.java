package com.example.producer.application.port.in;

import java.util.UUID;

public record OrderProposalAccepted(UUID eventId, UUID proposalId) {
}
