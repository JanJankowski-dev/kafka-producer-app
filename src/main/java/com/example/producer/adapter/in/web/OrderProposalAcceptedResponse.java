package com.example.producer.adapter.in.web;

import java.util.UUID;

public record OrderProposalAcceptedResponse(UUID eventId, UUID proposalId) {
}
