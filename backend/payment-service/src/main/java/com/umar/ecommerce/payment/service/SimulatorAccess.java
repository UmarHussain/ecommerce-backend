package com.umar.ecommerce.payment.service;

import com.umar.ecommerce.payment.config.PaymentSimulatorProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class SimulatorAccess {

    public static final String HEADER = "X-Simulator-Control";

    private final PaymentSimulatorProperties properties;

    public SimulatorAccess(PaymentSimulatorProperties properties) {
        this.properties = properties;
    }

    public void require(String presented) {
        if (!permitted(presented)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    boolean permitted(String presented) {
        String expected = properties.getControlToken() == null ? "" : properties.getControlToken();
        String actual = presented == null ? "" : presented;
        boolean matches = MessageDigest.isEqual(sha256(expected), sha256(actual));
        return properties.isControlEnabled() && !expected.isBlank() && matches;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
