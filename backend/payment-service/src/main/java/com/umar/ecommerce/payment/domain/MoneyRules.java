package com.umar.ecommerce.payment.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public final class MoneyRules {

    private static final BigDecimal MAX = new BigDecimal("9999999999.99");
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

    private MoneyRules() {
    }

    public static BigDecimal amount(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            BigDecimal value = node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText().trim());
            if (value.scale() > 2) {
                return null;
            }
            value = value.setScale(2);
            if (value.signum() <= 0 || value.compareTo(MAX) > 0) {
                return null;
            }
            return value;
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }

    public static String currency(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return CURRENCY.matcher(value).matches() ? value : null;
    }
}
