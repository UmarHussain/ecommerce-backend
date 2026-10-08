package com.umar.ecommerce.inventory.service;

import com.umar.ecommerce.inventory.catalog.CatalogLookupPort;
import com.umar.ecommerce.inventory.catalog.CatalogVariantSnapshot;
import com.umar.ecommerce.inventory.domain.OperationScope;
import com.umar.ecommerce.inventory.domain.RequestFingerprint;
import com.umar.ecommerce.inventory.dto.request.AdjustStockRequest;
import com.umar.ecommerce.inventory.dto.request.SetupStockRequest;
import com.umar.ecommerce.inventory.entity.InventoryCommand;
import com.umar.ecommerce.inventory.exception.InventoryProblem;
import com.umar.ecommerce.inventory.repository.InventoryCommandRepository;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Coordinates a stock command outside a database transaction. Catalog lookup
 * stays here. {@link StockTransactionService} owns the write, and constraint
 * or lock failures are handled only after that write transaction has ended.
 */
@Service
public class StockCommandService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
    private static final String COMMAND_CONSTRAINT = "uq_inventory_command_scope";
    private static final String VARIANT_CONSTRAINT = "uq_stock_item_catalog_variant";
    private static final String SKU_CONSTRAINT = "uq_stock_item_sku";

    private final InventoryCommandRepository commands;
    private final CatalogLookupPort catalog;
    private final StockTransactionService stockTransactions;

    public StockCommandService(
            InventoryCommandRepository commands,
            CatalogLookupPort catalog,
            StockTransactionService stockTransactions
    ) {
        this.commands = commands;
        this.catalog = catalog;
        this.stockTransactions = stockTransactions;
    }

    @Transactional(propagation = Propagation.NEVER)
    public CommandOutcome setup(
            Actor actor,
            Jwt jwt,
            String bearerToken,
            String correlationId,
            String idempotencyKey,
            SetupStockRequest request
    ) {
        requireKey(idempotencyKey);
        String note = RequestFingerprint.normalize(request.note());
        String reference = RequestFingerprint.normalize(request.reference());
        String fingerprint = RequestFingerprint.setup(
                request.catalogVariantId(),
                request.initialOnHand(),
                request.reasonCode(),
                note,
                reference
        );
        Optional<CommandOutcome> replay = completedReplay(
                actor,
                OperationScope.SETUP,
                idempotencyKey,
                fingerprint
        );
        if (replay.isPresent()) {
            return replay.get();
        }
        request.reasonCode().requireForSetup(request.initialOnHand());
        requireCatalogRead(jwt);
        CatalogVariantSnapshot snapshot = catalog.load(request.catalogVariantId(), bearerToken, correlationId);
        if (!snapshot.variantId().equals(request.catalogVariantId())) {
            throw new InventoryProblem(
                    HttpStatus.NOT_FOUND,
                    InventoryProblem.CATALOG_VARIANT_NOT_FOUND,
                    "Catalog returned a different variant identifier"
            );
        }
        if (!snapshot.sellableChain()) {
            throw new InventoryProblem(
                    HttpStatus.CONFLICT,
                    InventoryProblem.CATALOG_INACTIVE,
                    "The catalog variant, product, or category is inactive. "
                            + "Setup is a point-in-time check and does not create stock for an unsellable chain."
            );
        }
        try {
            return stockTransactions.writeSetup(new StockTransactionService.SetupWrite(
                    actor,
                    idempotencyKey,
                    fingerprint,
                    correlationId,
                    snapshot,
                    request.initialOnHand(),
                    note,
                    reference
            ));
        } catch (DataIntegrityViolationException exception) {
            return recoverSetup(exception, actor, idempotencyKey, fingerprint, correlationId);
        } catch (RuntimeException exception) {
            if (isLockTimeout(exception)) {
                throw CommandReplay.inProgress();
            }
            throw exception;
        }
    }

    @Transactional(propagation = Propagation.NEVER)
    public CommandOutcome adjust(
            Actor actor,
            String correlationId,
            UUID stockItemId,
            String idempotencyKey,
            AdjustStockRequest request
    ) {
        requireKey(idempotencyKey);
        String note = RequestFingerprint.normalize(request.note());
        String reference = RequestFingerprint.normalize(request.reference());
        String fingerprint = RequestFingerprint.adjustment(
                stockItemId,
                request.delta(),
                request.reasonCode(),
                note,
                reference,
                request.expectedVersion()
        );
        Optional<CommandOutcome> replay = completedReplay(
                actor,
                OperationScope.ADJUSTMENT,
                idempotencyKey,
                fingerprint
        );
        if (replay.isPresent()) {
            return replay.get();
        }
        try {
            return stockTransactions.writeAdjustment(new StockTransactionService.AdjustmentWrite(
                    actor,
                    stockItemId,
                    idempotencyKey,
                    fingerprint,
                    correlationId,
                    request.delta(),
                    request.reasonCode(),
                    request.expectedVersion(),
                    note,
                    reference
            ));
        } catch (DataIntegrityViolationException exception) {
            if (constraintName(exception).contains(COMMAND_CONSTRAINT)) {
                return commands.findByActorIssuerAndActorSubjectAndOperationScopeAndIdempotencyKey(
                        actor.issuer(),
                        actor.subject(),
                        OperationScope.ADJUSTMENT,
                        idempotencyKey
                ).filter(InventoryCommand::isCompleted)
                        .map(command -> CommandReplay.replay(command, fingerprint))
                        .orElseThrow(CommandReplay::inProgress);
            }
            throw exception;
        } catch (RuntimeException exception) {
            if (isLockTimeout(exception)) {
                throw CommandReplay.inProgress();
            }
            throw exception;
        }
    }

    private Optional<CommandOutcome> completedReplay(
            Actor actor,
            OperationScope scope,
            String idempotencyKey,
            String fingerprint
    ) {
        return commands.findByActorIssuerAndActorSubjectAndOperationScopeAndIdempotencyKey(
                actor.issuer(),
                actor.subject(),
                scope,
                idempotencyKey
        ).filter(InventoryCommand::isCompleted).map(command -> CommandReplay.replay(command, fingerprint));
    }

    private CommandOutcome recoverSetup(
            DataIntegrityViolationException exception,
            Actor actor,
            String idempotencyKey,
            String fingerprint,
            String correlationId
    ) {
        String constraint = constraintName(exception);
        if (constraint.contains(COMMAND_CONSTRAINT)) {
            return commands.findByActorIssuerAndActorSubjectAndOperationScopeAndIdempotencyKey(
                    actor.issuer(),
                    actor.subject(),
                    OperationScope.SETUP,
                    idempotencyKey
            ).filter(InventoryCommand::isCompleted)
                    .map(command -> CommandReplay.replay(command, fingerprint))
                    .orElseThrow(CommandReplay::inProgress);
        }
        if (constraint.contains(VARIANT_CONSTRAINT) || constraint.contains(SKU_CONSTRAINT)) {
            return stockTransactions.recordSetupConflict(new StockTransactionService.SetupConflictWrite(
                    actor,
                    idempotencyKey,
                    fingerprint,
                    correlationId
            ));
        }
        throw exception;
    }

    private static void requireKey(String idempotencyKey) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new InventoryProblem(
                    HttpStatus.BAD_REQUEST,
                    InventoryProblem.VALIDATION_FAILED,
                    "Idempotency-Key must be 1 to 128 letters, digits, or . _ : -"
            );
        }
    }

    private static void requireCatalogRead(Jwt jwt) {
        if (!Actor.hasClientRole(jwt, "catalog-service", "catalog.read")) {
            throw new InventoryProblem(
                    HttpStatus.FORBIDDEN,
                    InventoryProblem.CATALOG_READ_REQUIRED,
                    "Stock setup requires catalog.read on the catalog-service client"
            );
        }
    }

    private static boolean isLockTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof CannotAcquireLockException || current instanceof PessimisticLockingFailureException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("lock timeout")) {
                return true;
            }
            if ("55P03".equals(sqlState(current))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String sqlState(Throwable exception) {
        try {
            Object state = exception.getClass().getMethod("getSQLState").invoke(exception);
            return state == null ? null : state.toString();
        } catch (ReflectiveOperationException exceptionIgnored) {
            return null;
        }
    }

    private static String constraintName(Throwable exception) {
        StringBuilder text = new StringBuilder();
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null) {
                text.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return text.toString();
    }
}
