package com.umar.ecommerce.inventory.service;

/**
 * Stored HTTP result of one inventory command. Replay returns these bytes.
 */
public record CommandOutcome(int status, String contentType, String body, String location) {
}
