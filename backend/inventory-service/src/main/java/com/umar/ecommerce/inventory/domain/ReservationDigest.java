package com.umar.ecommerce.inventory.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Canonical hash of a checkout command. Line order does not change the hash.
 */
public final class ReservationDigest {

    private ReservationDigest() {
    }

    public static String reserve(UUID orderId, List<Line> lines) {
        StringBuilder builder = new StringBuilder();
        builder.append("RESERVE\n").append(orderId).append('\n');
        lines.stream()
                .sorted((left, right) -> left.catalogVariantId().compareTo(right.catalogVariantId()))
                .forEach(line -> builder
                        .append(line.catalogVariantId())
                        .append('\t')
                        .append(line.sku())
                        .append('\t')
                        .append(line.quantity())
                        .append('\n'));
        return hash(builder.toString());
    }

    public static String command(String eventType, UUID orderId) {
        return hash(eventType + "\n" + orderId);
    }

    public record Line(UUID catalogVariantId, String sku, int quantity) {
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
