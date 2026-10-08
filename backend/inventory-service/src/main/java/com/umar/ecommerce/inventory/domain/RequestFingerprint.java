package com.umar.ecommerce.inventory.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Stable fingerprint of the command the caller asked for. The same key with a
 * different fingerprint is a conflict, not a second stock change.
 */
public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    public static String setup(
            UUID catalogVariantId,
            int initialOnHand,
            ReasonCode reason,
            String note,
            String reference
    ) {
        return hash(
                "SETUP",
                "",
                catalogVariantId.toString(),
                Integer.toString(initialOnHand),
                reason.name(),
                normalize(note),
                normalize(reference),
                ""
        );
    }

    public static String adjustment(
            UUID stockItemId,
            int delta,
            ReasonCode reason,
            String note,
            String reference,
            long expectedVersion
    ) {
        return hash(
                "ADJUSTMENT",
                stockItemId.toString(),
                "",
                Integer.toString(delta),
                reason.name(),
                normalize(note),
                normalize(reference),
                Long.toString(expectedVersion)
        );
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private static String hash(String... parts) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", parts).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
