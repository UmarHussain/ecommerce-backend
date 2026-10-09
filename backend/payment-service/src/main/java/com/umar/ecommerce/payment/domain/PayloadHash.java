package com.umar.ecommerce.payment.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public final class PayloadHash {

    private PayloadHash() {
    }

    public static String charge(UUID orderId, BigDecimal amount, String currency) {
        return sha256(orderId + "|" + amount.toPlainString() + "|" + currency);
    }

    public static String refund(UUID orderId, UUID chargeOperationId, BigDecimal amount, String currency) {
        return sha256(orderId + "|" + chargeOperationId + "|" + amount.toPlainString() + "|" + currency);
    }

    private static String sha256(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
