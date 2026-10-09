package com.umar.ecommerce.cart.domain;

import com.umar.ecommerce.cart.exception.CartProblem;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.regex.Pattern;

public final class SkuRules {

    private static final Pattern SKU = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{2,63}$");

    private SkuRules() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("sku must not be blank");
        }
        String sku = value.trim().toUpperCase(Locale.ROOT);
        if (!SKU.matcher(sku).matches()) {
            throw invalid("sku must be 3 to 64 characters and contain only letters, numbers, dots, underscores, and hyphens");
        }
        return sku;
    }

    private static CartProblem invalid(String message) {
        return new CartProblem(HttpStatus.BAD_REQUEST, CartProblem.VALIDATION_FAILED, message);
    }
}
