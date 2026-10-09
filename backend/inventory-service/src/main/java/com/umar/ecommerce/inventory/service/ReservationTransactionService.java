package com.umar.ecommerce.inventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umar.ecommerce.inventory.domain.MovementType;
import com.umar.ecommerce.inventory.domain.ReservationCommandType;
import com.umar.ecommerce.inventory.domain.ReservationDigest;
import com.umar.ecommerce.inventory.domain.ReservationState;
import com.umar.ecommerce.inventory.entity.OutboxEvent;
import com.umar.ecommerce.inventory.entity.Reservation;
import com.umar.ecommerce.inventory.entity.ReservationCommand;
import com.umar.ecommerce.inventory.entity.ReservationHistory;
import com.umar.ecommerce.inventory.entity.ReservationLine;
import com.umar.ecommerce.inventory.entity.StockItem;
import com.umar.ecommerce.inventory.entity.StockMovement;
import com.umar.ecommerce.inventory.messaging.CheckoutCommand;
import com.umar.ecommerce.inventory.messaging.InventoryChannels;
import com.umar.ecommerce.inventory.messaging.OutboxReady;
import com.umar.ecommerce.inventory.repository.StockItemRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Commits a checkout stock command with its outbox row. Callers invoke the
 * Spring bean so the transaction commits before a Kafka acknowledgement.
 * Stock rows are locked one catalog variant id at a time, in sorted order.
 */
@Service
public class ReservationTransactionService {

    private final StockItemRepository stocks;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration prePaymentTtl;

    public ReservationTransactionService(
            StockItemRepository stocks,
            EntityManager entityManager,
            ObjectMapper objectMapper,
            ApplicationEventPublisher events,
            Clock clock,
            @Value("${inventory.reservation.pre-payment-ttl:2m}") Duration prePaymentTtl
    ) {
        this.stocks = stocks;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.events = events;
        this.clock = clock;
        this.prePaymentTtl = prePaymentTtl;
    }

    @Transactional
    public ReservationEffect apply(CheckoutCommand command) {
        setLockTimeout();
        if (!claimInbox(command.eventId())) {
            return new ReservationEffect(null, command.orderId());
        }
        lockOrder(command.orderId());
        String hash = hashOf(command);
        ReservationCommand existing = entityManager.find(
                ReservationCommand.class,
                command.commandId(),
                LockModeType.PESSIMISTIC_WRITE
        );
        if (existing != null) {
            if (!existing.getPayloadHash().equals(hash)) {
                return new ReservationEffect(null, command.orderId());
            }
            return new ReservationEffect(existing.getResultEventType(), command.orderId());
        }
        return switch (command.eventType()) {
            case InventoryChannels.RESERVE_STOCK -> reserve(command, hash);
            case InventoryChannels.HOLD_RESERVATION -> hold(command, hash);
            case InventoryChannels.RELEASE_RESERVATION -> release(command, hash);
            case InventoryChannels.CONSUME_RESERVATION -> consume(command, hash);
            case InventoryChannels.RESTOCK_RESERVATION -> restock(command, hash);
            default -> throw new IllegalArgumentException("Unsupported inventory command");
        };
    }

    @Transactional(readOnly = true)
    public List<UUID> dueIds(Instant now, int batch) {
        int limit = Math.max(1, Math.min(batch, 100));
        return entityManager.createQuery("""
                        select reservation.id from Reservation reservation
                        where reservation.state = :state
                          and reservation.expiresAt < :now
                        order by reservation.expiresAt, reservation.id
                        """, UUID.class)
                .setParameter("state", ReservationState.ACTIVE)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * Expires one ACTIVE reservation whose deadline has passed. A hold that
     * already owns the row leaves this method with no stock change.
     */
    @Transactional
    public Optional<ReservationEffect> expireIfDue(UUID reservationId) {
        setLockTimeout();
        UUID orderId = entityManager.createQuery("""
                        select reservation.orderId from Reservation reservation
                        where reservation.id = :id
                        """, UUID.class)
                .setParameter("id", reservationId)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (orderId == null) {
            return Optional.empty();
        }
        lockOrder(orderId);
        Reservation reservation = entityManager.find(Reservation.class, reservationId, LockModeType.PESSIMISTIC_WRITE);
        Instant now = clock.instant();
        if (reservation == null
                || reservation.getState() != ReservationState.ACTIVE
                || reservation.getExpiresAt() == null
                || !reservation.getExpiresAt().isBefore(now)) {
            return Optional.empty();
        }
        releaseLines(reservation);
        ReservationState from = reservation.getState();
        reservation.markReleased();
        entityManager.persist(ReservationHistory.record(
                reservation.getId(),
                reservation.getReserveCommandId(),
                from,
                ReservationState.RELEASED,
                "pre-payment ttl elapsed"
        ));
        entityManager.flush();
        appendOutbox(
                orderId,
                reservation.getReserveCommandId(),
                null,
                null,
                "reservation-expiry",
                reservation.getReserveCommandId(),
                InventoryChannels.RESERVATION_EXPIRED,
                reservation.getVersion(),
                orderPayload(orderId)
        );
        return Optional.of(new ReservationEffect(InventoryChannels.RESERVATION_EXPIRED, orderId));
    }

    private ReservationEffect reserve(CheckoutCommand command, String hash) {
        if (tombstoneExists(command.orderId())) {
            return complete(
                    command,
                    hash,
                    ReservationCommandType.RESERVE,
                    InventoryChannels.STOCK_REJECTED,
                    null,
                    reasonPayload(command.orderId(), "TOMBSTONED"),
                    List.of()
            );
        }
        if (lockReservation(command.orderId()) != null) {
            return complete(
                    command,
                    hash,
                    ReservationCommandType.RESERVE,
                    InventoryChannels.STOCK_REJECTED,
                    null,
                    reasonPayload(command.orderId(), "ALREADY_RESERVED"),
                    List.of()
            );
        }
        String problem = lineProblem(command.lines());
        if (problem != null) {
            return complete(
                    command,
                    hash,
                    ReservationCommandType.RESERVE,
                    InventoryChannels.STOCK_REJECTED,
                    null,
                    reasonPayload(command.orderId(), problem),
                    List.of()
            );
        }
        List<ReservationDigest.Line> sorted = sorted(command.lines());
        List<StockItem> locked = new ArrayList<>();
        for (ReservationDigest.Line line : sorted) {
            StockItem item = stocks.lockByCatalogVariantId(line.catalogVariantId()).orElse(null);
            if (item == null || !item.getSku().equals(line.sku())) {
                return complete(
                        command,
                        hash,
                        ReservationCommandType.RESERVE,
                        InventoryChannels.STOCK_REJECTED,
                        null,
                        reasonPayload(command.orderId(), "UNKNOWN_SKU"),
                        List.of()
                );
            }
            if (item.available() < line.quantity()) {
                return complete(
                        command,
                        hash,
                        ReservationCommandType.RESERVE,
                        InventoryChannels.STOCK_REJECTED,
                        null,
                        reasonPayload(command.orderId(), "INSUFFICIENT_AVAILABLE"),
                        List.of()
                );
            }
            locked.add(item);
        }
        for (int index = 0; index < sorted.size(); index++) {
            locked.get(index).reserve(sorted.get(index).quantity());
        }
        Reservation reservation = Reservation.open(
                command.orderId(),
                hash,
                command.commandId(),
                clock.instant().plus(prePaymentTtl)
        );
        entityManager.persist(reservation);
        List<Map<String, Object>> payloadLines = new ArrayList<>();
        for (ReservationDigest.Line line : sorted) {
            entityManager.persist(ReservationLine.create(
                    reservation,
                    line.catalogVariantId(),
                    line.sku(),
                    line.quantity()
            ));
            Map<String, Object> payloadLine = new LinkedHashMap<>();
            payloadLine.put("catalogVariantId", line.catalogVariantId());
            payloadLine.put("sku", line.sku());
            payloadLine.put("quantity", line.quantity());
            payloadLines.add(payloadLine);
        }
        entityManager.flush();
        entityManager.persist(ReservationHistory.record(
                reservation.getId(),
                command.commandId(),
                null,
                ReservationState.ACTIVE,
                "reserved"
        ));
        Map<String, Object> payload = orderPayload(command.orderId());
        payload.put("lines", payloadLines);
        return complete(
                command,
                hash,
                ReservationCommandType.RESERVE,
                InventoryChannels.STOCK_RESERVED,
                reservation,
                payload,
                List.of()
        );
    }

    private ReservationEffect hold(CheckoutCommand command, String hash) {
        Reservation reservation = lockReservation(command.orderId());
        if (reservation == null) {
            return complete(
                    command,
                    hash,
                    ReservationCommandType.HOLD,
                    InventoryChannels.HOLD_REJECTED,
                    null,
                    reasonPayload(command.orderId(), "NOT_RESERVED"),
                    List.of()
            );
        }
        if (reservation.getState() == ReservationState.CHECKOUT_HELD) {
            return complete(
                    command,
                    hash,
                    ReservationCommandType.HOLD,
                    InventoryChannels.RESERVATION_HELD,
                    reservation,
                    orderPayload(command.orderId()),
                    List.of()
            );
        }
        if (reservation.getState() != ReservationState.ACTIVE) {
            return complete(
                    command,
                    hash,
                    ReservationCommandType.HOLD,
                    InventoryChannels.HOLD_REJECTED,
                    reservation,
                    reasonPayload(command.orderId(), "NOT_ACTIVE"),
                    List.of()
            );
        }
        ReservationState from = reservation.getState();
        reservation.markHeld();
        entityManager.persist(ReservationHistory.record(
                reservation.getId(),
                command.commandId(),
                from,
                ReservationState.CHECKOUT_HELD,
                "held for payment"
        ));
        return complete(
                command,
                hash,
                ReservationCommandType.HOLD,
                InventoryChannels.RESERVATION_HELD,
                reservation,
                orderPayload(command.orderId()),
                List.of()
        );
    }

    private ReservationEffect release(CheckoutCommand command, String hash) {
        Reservation reservation = lockReservation(command.orderId());
        if (reservation == null) {
            insertTombstone(command.orderId());
            return complete(
                    command,
                    hash,
                    ReservationCommandType.RELEASE,
                    InventoryChannels.STOCK_RELEASED,
                    null,
                    reasonPayload(command.orderId(), "RELEASED_BEFORE_RESERVE"),
                    List.of()
            );
        }
        if (reservation.getState() == ReservationState.RELEASED) {
            return complete(
                    command,
                    hash,
                    ReservationCommandType.RELEASE,
                    InventoryChannels.STOCK_RELEASED,
                    reservation,
                    orderPayload(command.orderId()),
                    List.of()
            );
        }
        if (reservation.getState() != ReservationState.ACTIVE
                && reservation.getState() != ReservationState.CHECKOUT_HELD) {
            return complete(
                    command,
                    hash,
                    ReservationCommandType.RELEASE,
                    InventoryChannels.RELEASE_REJECTED,
                    reservation,
                    reasonPayload(command.orderId(), "NOT_RELEASABLE"),
                    List.of()
            );
        }
        releaseLines(reservation);
        ReservationState from = reservation.getState();
        reservation.markReleased();
        entityManager.persist(ReservationHistory.record(
                reservation.getId(),
                command.commandId(),
                from,
                ReservationState.RELEASED,
                "released"
        ));
        return complete(
                command,
                hash,
                ReservationCommandType.RELEASE,
                InventoryChannels.STOCK_RELEASED,
                reservation,
                orderPayload(command.orderId()),
                List.of()
        );
    }

    private ReservationEffect consume(CheckoutCommand command, String hash) {
        Reservation reservation = lockReservation(command.orderId());
        if (reservation == null || reservation.getState() != ReservationState.CHECKOUT_HELD) {
            return complete(
                    command,
                    hash,
                    ReservationCommandType.CONSUME,
                    InventoryChannels.CONSUME_REJECTED,
                    reservation,
                    reasonPayload(command.orderId(), "NOT_HELD"),
                    List.of()
            );
        }
        List<StockMovement> movements = new ArrayList<>();
        for (ReservationLine line : sortedLines(reservation)) {
            StockItem item = requireStock(line.getCatalogVariantId());
            int beforeOnHand = item.getOnHand();
            int beforeReserved = item.getReserved();
            item.consumeReserved(line.getQuantity());
            movements.add(StockMovement.record(
                    item.getId(),
                    reservation.getId(),
                    command.commandId(),
                    MovementType.CONSUME,
                    line.getQuantity(),
                    beforeOnHand,
                    item.getOnHand(),
                    beforeReserved,
                    item.getReserved()
            ));
        }
        ReservationState from = reservation.getState();
        reservation.markConsumed();
        entityManager.persist(ReservationHistory.record(
                reservation.getId(),
                command.commandId(),
                from,
                ReservationState.CONSUMED,
                "consumed"
        ));
        return complete(
                command,
                hash,
                ReservationCommandType.CONSUME,
                InventoryChannels.STOCK_CONSUMED,
                reservation,
                orderPayload(command.orderId()),
                movements
        );
    }

    private ReservationEffect restock(CheckoutCommand command, String hash) {
        Reservation reservation = lockReservation(command.orderId());
        if (reservation == null || reservation.getState() != ReservationState.CONSUMED) {
            return complete(
                    command,
                    hash,
                    ReservationCommandType.RESTOCK,
                    InventoryChannels.RESTOCK_REJECTED,
                    reservation,
                    reasonPayload(command.orderId(), "NOT_CONSUMED"),
                    List.of()
            );
        }
        List<StockMovement> movements = new ArrayList<>();
        for (ReservationLine line : sortedLines(reservation)) {
            StockItem item = requireStock(line.getCatalogVariantId());
            int beforeOnHand = item.getOnHand();
            int beforeReserved = item.getReserved();
            item.restock(line.getQuantity());
            movements.add(StockMovement.record(
                    item.getId(),
                    reservation.getId(),
                    command.commandId(),
                    MovementType.RESTOCK,
                    line.getQuantity(),
                    beforeOnHand,
                    item.getOnHand(),
                    beforeReserved,
                    item.getReserved()
            ));
        }
        ReservationState from = reservation.getState();
        reservation.markRestocked();
        entityManager.persist(ReservationHistory.record(
                reservation.getId(),
                command.commandId(),
                from,
                ReservationState.RESTOCKED,
                "restocked"
        ));
        return complete(
                command,
                hash,
                ReservationCommandType.RESTOCK,
                InventoryChannels.STOCK_RESTOCKED,
                reservation,
                orderPayload(command.orderId()),
                movements
        );
    }

    private ReservationEffect complete(
            CheckoutCommand command,
            String hash,
            ReservationCommandType type,
            String eventType,
            Reservation reservation,
            Map<String, Object> payload,
            List<StockMovement> movements
    ) {
        entityManager.persist(ReservationCommand.recorded(
                command.commandId(),
                command.orderId(),
                type,
                hash,
                eventType
        ));
        entityManager.flush();
        for (StockMovement movement : movements) {
            entityManager.persist(movement);
        }
        long aggregateVersion = 0;
        if (reservation != null) {
            entityManager.flush();
            aggregateVersion = reservation.getVersion();
        }
        appendOutbox(
                command.orderId(),
                command.commandId(),
                command.sagaId(),
                command.eventId(),
                correlation(command.correlationId()),
                command.commandId(),
                eventType,
                aggregateVersion,
                payload
        );
        return new ReservationEffect(eventType, command.orderId());
    }

    private void appendOutbox(
            UUID orderId,
            UUID commandId,
            UUID sagaId,
            UUID causationId,
            String correlationId,
            UUID envelopeCommandId,
            String eventType,
            long aggregateVersion,
            Map<String, Object> payload
    ) {
        UUID eventId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", InventoryChannels.VERSION);
        envelope.put("aggregateId", orderId);
        envelope.put("aggregateVersion", aggregateVersion);
        envelope.put("sagaId", sagaId);
        envelope.put("commandId", envelopeCommandId);
        envelope.put("correlationId", correlationId);
        envelope.put("causationId", causationId);
        envelope.put("occurredAt", clock.instant());
        envelope.put("payload", payload);
        Instant now = clock.instant();
        entityManager.persist(OutboxEvent.pending(
                outboxId,
                eventId,
                eventType,
                orderId,
                aggregateVersion,
                sagaId,
                commandId,
                correlationId,
                causationId,
                writeJson(payload),
                writeJson(envelope),
                now
        ));
        events.publishEvent(new OutboxReady(outboxId));
    }

    private void releaseLines(Reservation reservation) {
        for (ReservationLine line : sortedLines(reservation)) {
            requireStock(line.getCatalogVariantId()).releaseReserved(line.getQuantity());
        }
    }

    private StockItem requireStock(UUID catalogVariantId) {
        return stocks.lockByCatalogVariantId(catalogVariantId)
                .orElseThrow(() -> new IllegalStateException("Reserved stock row is missing"));
    }

    private List<ReservationLine> sortedLines(Reservation reservation) {
        return entityManager.createQuery("""
                        select line from ReservationLine line
                        where line.reservation = :reservation
                        order by line.catalogVariantId
                        """, ReservationLine.class)
                .setParameter("reservation", reservation)
                .getResultList();
    }

    private Reservation lockReservation(UUID orderId) {
        List<Reservation> rows = entityManager.createQuery("""
                        select reservation from Reservation reservation
                        where reservation.orderId = :orderId
                        """, Reservation.class)
                .setParameter("orderId", orderId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean claimInbox(UUID eventId) {
        int inserted = entityManager.createNativeQuery("""
                        insert into inbox_event (consumer_name, event_id, processed_at)
                        values (:consumer, :eventId, now())
                        on conflict do nothing
                        """)
                .setParameter("consumer", InventoryChannels.GROUP)
                .setParameter("eventId", eventId)
                .executeUpdate();
        return inserted == 1;
    }

    private boolean tombstoneExists(UUID orderId) {
        Number count = (Number) entityManager.createNativeQuery("""
                        select count(*) from reservation_tombstone where order_id = :orderId
                        """)
                .setParameter("orderId", orderId)
                .getSingleResult();
        return count.intValue() > 0;
    }

    private void insertTombstone(UUID orderId) {
        entityManager.createNativeQuery("""
                        insert into reservation_tombstone (order_id, created_at)
                        values (:orderId, now())
                        on conflict do nothing
                        """)
                .setParameter("orderId", orderId)
                .executeUpdate();
    }

    private void lockOrder(UUID orderId) {
        long orderKey = orderId.getMostSignificantBits() ^ orderId.getLeastSignificantBits();
        entityManager.createNativeQuery("select pg_advisory_xact_lock(cast(:orderKey as bigint))")
                .setParameter("orderKey", orderKey)
                .getSingleResult();
    }

    private void setLockTimeout() {
        entityManager.createNativeQuery("select set_config('lock_timeout', '2s', true)").getSingleResult();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("The reservation outcome could not be stored", exception);
        }
    }

    private static String hashOf(CheckoutCommand command) {
        if (InventoryChannels.RESERVE_STOCK.equals(command.eventType())) {
            return ReservationDigest.reserve(command.orderId(), command.lines() == null ? List.of() : command.lines());
        }
        return ReservationDigest.command(command.eventType(), command.orderId());
    }

    private static String lineProblem(List<ReservationDigest.Line> lines) {
        if (lines == null || lines.isEmpty()) {
            return "INVALID_QUANTITY";
        }
        Set<UUID> seen = new HashSet<>();
        for (ReservationDigest.Line line : lines) {
            if (line.catalogVariantId() == null || line.sku() == null || line.sku().isBlank()) {
                return "UNKNOWN_SKU";
            }
            if (line.quantity() < 1 || line.quantity() > 99) {
                return "INVALID_QUANTITY";
            }
            if (!seen.add(line.catalogVariantId())) {
                return "DUPLICATE_LINE";
            }
        }
        return null;
    }

    private static List<ReservationDigest.Line> sorted(List<ReservationDigest.Line> lines) {
        return lines.stream()
                .sorted(Comparator.comparing(ReservationDigest.Line::catalogVariantId))
                .toList();
    }

    private static Map<String, Object> orderPayload(UUID orderId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        return payload;
    }

    private static Map<String, Object> reasonPayload(UUID orderId, String reason) {
        Map<String, Object> payload = orderPayload(orderId);
        payload.put("reason", reason);
        return payload;
    }

    private static String correlation(String value) {
        if (value == null || value.isBlank()) {
            return "unavailable";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80);
    }

    public record ReservationEffect(String eventType, UUID orderId) {
    }
}
