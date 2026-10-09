package com.umar.ecommerce.order.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyPolicy {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final String SHIPPING_POLICY = "LOCAL_DEMO_FREE_SHIPPING";
    public static final String TAX_POLICY = "LOCAL_DEMO_TAX_NOT_CALCULATED";
    public static final BigDecimal ZERO = new BigDecimal("0.00");

    private MoneyPolicy() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        return money(unitPrice).multiply(BigDecimal.valueOf(quantity)).setScale(SCALE, ROUNDING);
    }
}
