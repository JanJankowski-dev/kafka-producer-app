package com.example.producer.application.service;

import com.example.producer.application.port.in.ProposeOrderCommand;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class OrderProposalFingerprint {
    private OrderProposalFingerprint() {
    }

    static String from(ProposeOrderCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            add(digest, command.portfolioId().value().toString());
            add(digest, command.instrumentId().value().toString());
            add(digest, command.side().name());
            add(digest, command.maxNotional().amount().stripTrailingZeros().toPlainString());
            add(digest, command.maxNotional().currency().getCurrencyCode());
            add(digest, command.source().name());
            add(digest, command.rationale());
            add(digest, Integer.toString(command.evidenceSources().size()));
            command.evidenceSources().forEach(source -> add(digest, source));
            add(digest, command.confidence().value().stripTrailingZeros().toPlainString());
            add(digest, command.validUntil().toString());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
