package com.umar.ecommerce.inventory.service;

import com.umar.ecommerce.inventory.entity.InventoryCommand;
import com.umar.ecommerce.inventory.exception.InventoryProblem;
import org.springframework.http.HttpStatus;

/**
 * Exact replay of a stored command result. Shared by the coordinator and the
 * writer so neither has to call the other.
 */
final class CommandReplay {

    private CommandReplay() {
    }

    static CommandOutcome replay(InventoryCommand command, String fingerprint) {
        if (!command.getRequestFingerprint().equals(fingerprint)) {
            throw new InventoryProblem(
                    HttpStatus.CONFLICT,
                    InventoryProblem.IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key was reused with a different request"
            );
        }
        return new CommandOutcome(
                command.getHttpStatus(),
                command.getContentType(),
                command.getResponseBody(),
                command.getLocation()
        );
    }

    static InventoryProblem inProgress() {
        return new InventoryProblem(
                HttpStatus.CONFLICT,
                InventoryProblem.COMMAND_IN_PROGRESS,
                "This command is still in progress; retry the same Idempotency-Key and payload"
        );
    }
}
