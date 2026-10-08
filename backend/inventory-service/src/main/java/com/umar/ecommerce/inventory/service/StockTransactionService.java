package com.umar.ecommerce.inventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umar.ecommerce.inventory.catalog.CatalogVariantSnapshot;
import com.umar.ecommerce.inventory.domain.OperationScope;
import com.umar.ecommerce.inventory.domain.ReasonCode;
import com.umar.ecommerce.inventory.dto.response.StockCommandResponse;
import com.umar.ecommerce.inventory.entity.InventoryCommand;
import com.umar.ecommerce.inventory.entity.StockAdjustment;
import com.umar.ecommerce.inventory.entity.StockItem;
import com.umar.ecommerce.inventory.exception.InventoryProblem;
import com.umar.ecommerce.inventory.repository.InventoryCommandRepository;
import com.umar.ecommerce.inventory.repository.StockAdjustmentRepository;
import com.umar.ecommerce.inventory.repository.StockItemRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Database write for one stock command. Call these methods on the Spring bean
 * so {@code REQUIRED} starts or joins a transaction. Private helpers run inside
 * that transaction. The coordinator invokes them only after catalog lookup and
 * only catches database failures after this transaction has ended.
 */
@Service
public class StockTransactionService {

    static final String JSON = "application/json";
    static final String PROBLEM = "application/problem+json";

    private final StockItemRepository stocks;
    private final StockAdjustmentRepository adjustments;
    private final InventoryCommandRepository commands;
    private final InventoryMapper mapper;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public StockTransactionService(
            StockItemRepository stocks,
            StockAdjustmentRepository adjustments,
            InventoryCommandRepository commands,
            InventoryMapper mapper,
            ObjectMapper objectMapper,
            EntityManager entityManager
    ) {
        this.stocks = stocks;
        this.adjustments = adjustments;
        this.commands = commands;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public CommandOutcome writeSetup(SetupWrite write) {
        setLockTimeout();
        Claim claim = claim(write.actor(), OperationScope.SETUP, write.idempotencyKey(), write.fingerprint());
        if (claim.replay() != null) {
            return claim.replay();
        }
        if (stocks.existsByCatalogVariantId(write.snapshot().variantId())) {
            return finishProblem(claim.command(), variantConflict(write.correlationId()));
        }
        StockItem item = StockItem.open(
                write.snapshot().variantId(),
                write.snapshot().sku(),
                write.snapshot().productName(),
                write.snapshot().variantName(),
                write.initialOnHand()
        );
        stocks.saveAndFlush(item);
        StockAdjustment opening = StockAdjustment.record(
                item,
                OperationScope.SETUP,
                write.initialOnHand(),
                0,
                write.initialOnHand(),
                item.getReserved(),
                item.getVersion(),
                ReasonCode.OPENING_BALANCE,
                emptyToNull(write.note()),
                emptyToNull(write.reference()),
                write.actor().issuer(),
                write.actor().subject()
        );
        adjustments.saveAndFlush(opening);
        StockCommandResponse response = new StockCommandResponse(
                mapper.toStockItem(item),
                mapper.toAdjustment(opening)
        );
        String location = "/api/v1/admin/inventory/stock-items/" + item.getId();
        return finishSuccess(claim.command(), HttpStatus.CREATED.value(), location, response);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public CommandOutcome writeAdjustment(AdjustmentWrite write) {
        setLockTimeout();
        Claim claim = claim(write.actor(), OperationScope.ADJUSTMENT, write.idempotencyKey(), write.fingerprint());
        if (claim.replay() != null) {
            return claim.replay();
        }
        StockItem item = stocks.lockById(write.stockItemId()).orElse(null);
        if (item == null) {
            return finishProblem(claim.command(), problem(
                    HttpStatus.NOT_FOUND,
                    InventoryProblem.NOT_FOUND,
                    "Stock item was not found",
                    write.correlationId()
            ));
        }
        if (item.getVersion() != write.expectedVersion()) {
            return finishProblem(claim.command(), problem(
                    HttpStatus.CONFLICT,
                    InventoryProblem.STALE_VERSION,
                    "Stock changed since it was loaded; reload and review the current values",
                    write.correlationId()
            ));
        }
        try {
            write.reason().requireForAdjustment(write.delta());
        } catch (InventoryProblem validation) {
            return finishProblem(claim.command(), copy(validation, write.correlationId()));
        }
        int before = item.getOnHand();
        int after;
        try {
            after = Math.addExact(before, write.delta());
        } catch (ArithmeticException exception) {
            return finishProblem(claim.command(), problem(
                    HttpStatus.BAD_REQUEST,
                    InventoryProblem.QUANTITY_OVERFLOW,
                    "The adjustment overflows the quantity range",
                    write.correlationId()
            ));
        }
        if (after < 0 || item.getReserved() > after) {
            return finishProblem(claim.command(), problem(
                    HttpStatus.CONFLICT,
                    InventoryProblem.STOCK_INVARIANT,
                    "On-hand cannot be negative or less than reserved",
                    write.correlationId()
            ));
        }
        item.commitOnHand(after);
        entityManager.flush();
        StockAdjustment adjustment = StockAdjustment.record(
                item,
                OperationScope.ADJUSTMENT,
                write.delta(),
                before,
                after,
                item.getReserved(),
                item.getVersion(),
                write.reason(),
                emptyToNull(write.note()),
                emptyToNull(write.reference()),
                write.actor().issuer(),
                write.actor().subject()
        );
        adjustments.saveAndFlush(adjustment);
        StockCommandResponse response = new StockCommandResponse(
                mapper.toStockItem(item),
                mapper.toAdjustment(adjustment)
        );
        return finishSuccess(claim.command(), HttpStatus.OK.value(), null, response);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public CommandOutcome recordSetupConflict(SetupConflictWrite write) {
        setLockTimeout();
        Claim claim = claim(write.actor(), OperationScope.SETUP, write.idempotencyKey(), write.fingerprint());
        if (claim.replay() != null) {
            return claim.replay();
        }
        return finishProblem(claim.command(), variantConflict(write.correlationId()));
    }

    private Claim claim(Actor actor, OperationScope scope, String idempotencyKey, String fingerprint) {
        Optional<InventoryCommand> existing = commands.lockByIdentity(
                actor.issuer(),
                actor.subject(),
                scope,
                idempotencyKey
        );
        if (existing.isPresent()) {
            InventoryCommand command = existing.get();
            if (!command.isCompleted()) {
                throw CommandReplay.inProgress();
            }
            return Claim.replay(CommandReplay.replay(command, fingerprint));
        }
        InventoryCommand created = InventoryCommand.start(
                actor.issuer(),
                actor.subject(),
                scope,
                idempotencyKey,
                fingerprint
        );
        commands.saveAndFlush(created);
        return Claim.fresh(created);
    }

    private CommandOutcome finishSuccess(
            InventoryCommand command,
            int status,
            String location,
            StockCommandResponse response
    ) {
        String body = writeJson(response);
        command.complete(status, JSON, body, location);
        entityManager.flush();
        return new CommandOutcome(status, JSON, body, location);
    }

    private CommandOutcome finishProblem(InventoryCommand command, Map<String, Object> problem) {
        int status = ((Number) problem.get("status")).intValue();
        String body = writeJson(problem);
        command.complete(status, PROBLEM, body, null);
        entityManager.flush();
        return new CommandOutcome(status, PROBLEM, body, null);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new InventoryProblem(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    InventoryProblem.INTERNAL_ERROR,
                    "The inventory result could not be stored"
            );
        }
    }

    private void setLockTimeout() {
        entityManager.createNativeQuery("select set_config('lock_timeout', '2s', true)").getSingleResult();
    }

    private static Map<String, Object> variantConflict(String correlationId) {
        return problem(
                HttpStatus.CONFLICT,
                InventoryProblem.VARIANT_ALREADY_STOCKED,
                "Stock is already set up for this catalog variant",
                correlationId
        );
    }

    private static Map<String, Object> copy(InventoryProblem source, String correlationId) {
        return problem(source.status(), source.code(), source.getMessage(), correlationId);
    }

    private static Map<String, Object> problem(HttpStatus status, String code, String detail, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("code", code);
        body.put("correlationId", correlationId == null ? "unavailable" : correlationId);
        body.put("timestamp", Instant.now().toString());
        return body;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    public record SetupWrite(
            Actor actor,
            String idempotencyKey,
            String fingerprint,
            String correlationId,
            CatalogVariantSnapshot snapshot,
            int initialOnHand,
            String note,
            String reference
    ) {
    }

    public record AdjustmentWrite(
            Actor actor,
            UUID stockItemId,
            String idempotencyKey,
            String fingerprint,
            String correlationId,
            int delta,
            ReasonCode reason,
            long expectedVersion,
            String note,
            String reference
    ) {
    }

    public record SetupConflictWrite(
            Actor actor,
            String idempotencyKey,
            String fingerprint,
            String correlationId
    ) {
    }

    private record Claim(InventoryCommand command, CommandOutcome replay) {
        static Claim fresh(InventoryCommand command) {
            return new Claim(command, null);
        }

        static Claim replay(CommandOutcome outcome) {
            return new Claim(null, outcome);
        }
    }
}
