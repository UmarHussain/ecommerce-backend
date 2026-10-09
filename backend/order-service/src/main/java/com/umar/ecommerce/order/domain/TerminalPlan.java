package com.umar.ecommerce.order.domain;

/**
 * How a compensation path is allowed to finish. A terminal order is not
 * reopened into {@code CONFIRMED}.
 */
public enum TerminalPlan {
    NONE,
    REJECT,
    CANCEL
}
