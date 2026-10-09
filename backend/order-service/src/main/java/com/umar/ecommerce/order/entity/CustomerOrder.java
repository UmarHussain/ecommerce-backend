package com.umar.ecommerce.order.entity;

import com.umar.ecommerce.order.domain.CleanupStatus;
import com.umar.ecommerce.order.domain.FulfilmentStatus;
import com.umar.ecommerce.order.domain.OrderStatus;
import com.umar.ecommerce.order.domain.PaymentStatus;
import com.umar.ecommerce.order.domain.SagaPolicy;
import com.umar.ecommerce.order.domain.SagaStep;
import com.umar.ecommerce.order.domain.TerminalPlan;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_issuer", nullable = false, updatable = false, length = 500)
    private String ownerIssuer;

    @Column(name = "owner_subject", nullable = false, updatable = false, length = 255)
    private String ownerSubject;

    @Column(name = "quote_id", nullable = false, updatable = false)
    private UUID quoteId;

    @Column(name = "cart_version", nullable = false, updatable = false)
    private long cartVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 32)
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 32)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfilment_status", nullable = false, length = 32)
    private FulfilmentStatus fulfilmentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "saga_step", nullable = false, length = 32)
    private SagaStep sagaStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "terminal_plan", nullable = false, length = 16)
    private TerminalPlan terminalPlan;

    @Column(name = "cancellation_requested", nullable = false)
    private boolean cancellationRequested;

    @Column(name = "stock_consumed", nullable = false)
    private boolean stockConsumed;

    @Enumerated(EnumType.STRING)
    @Column(name = "cleanup_status", nullable = false, length = 32)
    private CleanupStatus cleanupStatus;

    @Column(nullable = false, length = 300)
    private String obligation;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "merchandise_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal merchandiseTotal;

    @Column(name = "shipping_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingTotal;

    @Column(name = "tax_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxTotal;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal grandTotal;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "reserve_command_id", nullable = false, updatable = false)
    private UUID reserveCommandId;

    @Column(name = "hold_command_id")
    private UUID holdCommandId;

    @Column(name = "payment_command_id")
    private UUID paymentCommandId;

    @Column(name = "consume_command_id")
    private UUID consumeCommandId;

    @Column(name = "release_command_id")
    private UUID releaseCommandId;

    @Column(name = "refund_command_id")
    private UUID refundCommandId;

    @Column(name = "restock_command_id")
    private UUID restockCommandId;

    @Column(name = "cleanup_command_id")
    private UUID cleanupCommandId;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sku asc")
    private List<OrderLine> lines = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private OrderAddress address;

    protected CustomerOrder() {
    }

    public static CustomerOrder accept(
            UUID id,
            CustomerQuote quote,
            UUID reserveCommandId,
            Instant now,
            Instant deadlineAt
    ) {
        CustomerOrder order = new CustomerOrder();
        order.id = id;
        order.ownerIssuer = quote.getOwnerIssuer();
        order.ownerSubject = quote.getOwnerSubject();
        order.quoteId = quote.getId();
        order.cartVersion = quote.getCartVersion();
        SagaPolicy.State accepted = SagaPolicy.accepted();
        order.orderStatus = accepted.orderStatus();
        order.paymentStatus = accepted.paymentStatus();
        order.fulfilmentStatus = accepted.fulfilmentStatus();
        order.sagaStep = accepted.step();
        order.terminalPlan = accepted.plan();
        order.cancellationRequested = false;
        order.stockConsumed = false;
        order.cleanupStatus = accepted.cleanupStatus();
        order.obligation = "";
        order.currency = quote.getCurrency();
        order.merchandiseTotal = quote.getMerchandiseTotal();
        order.shippingTotal = quote.getShippingTotal();
        order.taxTotal = quote.getTaxTotal();
        order.grandTotal = quote.getGrandTotal();
        order.attempts = 0;
        order.reserveCommandId = reserveCommandId;
        order.deadlineAt = deadlineAt;
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }

    public void addLine(OrderLine line) {
        lines.add(line);
        line.assignOrder(this);
    }

    public void assignAddress(OrderAddress address) {
        this.address = address;
        address.assignOrder(this);
    }

    public void apply(SagaPolicy.State state, Instant now, Instant deadlineAt) {
        this.orderStatus = state.orderStatus();
        this.paymentStatus = state.paymentStatus();
        this.fulfilmentStatus = state.fulfilmentStatus();
        this.sagaStep = state.step();
        this.terminalPlan = state.plan();
        this.cancellationRequested = state.cancellationRequested();
        this.stockConsumed = state.stockConsumed();
        this.cleanupStatus = state.cleanupStatus();
        this.obligation = state.obligation() == null ? "" : state.obligation();
        this.attempts = state.attempts();
        this.deadlineAt = deadlineAt;
        this.updatedAt = now;
    }

    public SagaPolicy.State state() {
        return new SagaPolicy.State(
                sagaStep,
                orderStatus,
                paymentStatus,
                fulfilmentStatus,
                terminalPlan,
                cancellationRequested,
                stockConsumed,
                attempts,
                obligation,
                cleanupStatus
        );
    }

    public UUID commandId(SagaPolicy.Action action, UUID created) {
        return switch (action) {
            case RESERVE -> reserveCommandId;
            case HOLD -> holdCommandId = existing(holdCommandId, created);
            case PAYMENT, QUERY_PAYMENT -> paymentCommandId = existing(paymentCommandId, created);
            case CONSUME -> consumeCommandId = existing(consumeCommandId, created);
            case RELEASE -> releaseCommandId = existing(releaseCommandId, created);
            case REFUND -> refundCommandId = existing(refundCommandId, created);
            case RESTOCK -> restockCommandId = existing(restockCommandId, created);
            case CLEANUP -> cleanupCommandId = existing(cleanupCommandId, created);
            case NONE -> null;
        };
    }

    public boolean accepts(SagaPolicy.SignalType type, UUID commandId) {
        if (type == SagaPolicy.SignalType.DEADLINE || type == SagaPolicy.SignalType.CANCEL_REQUESTED) {
            return true;
        }
        if (type == SagaPolicy.SignalType.RESERVATION_EXPIRED) {
            return commandId != null && (commandId.equals(reserveCommandId) || commandId.equals(holdCommandId));
        }
        UUID expected = expectedCommand(type);
        return expected != null && expected.equals(commandId);
    }

    public UUID expectedCommand(SagaPolicy.SignalType type) {
        return switch (type) {
            case STOCK_RESERVED, STOCK_REJECTED -> reserveCommandId;
            case RESERVATION_HELD, HOLD_REJECTED, RESERVATION_EXPIRED -> holdCommandId == null
                    ? reserveCommandId : holdCommandId;
            case PAYMENT_SUCCEEDED, PAYMENT_DECLINED, PAYMENT_UNKNOWN, PAYMENT_CONFLICT -> paymentCommandId;
            case STOCK_CONSUMED, CONSUME_REJECTED -> consumeCommandId;
            case STOCK_RELEASED, RELEASE_REJECTED -> releaseCommandId;
            case PAYMENT_REFUNDED, REFUND_FAILED, REFUND_CONFLICT -> refundCommandId;
            case STOCK_RESTOCKED, RESTOCK_REJECTED -> restockCommandId;
            case CART_CLEARED, CART_CLEANUP_SKIPPED -> cleanupCommandId;
            case DEADLINE, CANCEL_REQUESTED -> null;
        };
    }

    private static UUID existing(UUID current, UUID created) {
        return current == null ? created : current;
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerIssuer() {
        return ownerIssuer;
    }

    public String getOwnerSubject() {
        return ownerSubject;
    }

    public UUID getQuoteId() {
        return quoteId;
    }

    public long getCartVersion() {
        return cartVersion;
    }

    public OrderStatus getOrderStatus() {
        return orderStatus;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public FulfilmentStatus getFulfilmentStatus() {
        return fulfilmentStatus;
    }

    public SagaStep getSagaStep() {
        return sagaStep;
    }

    public boolean isCancellationRequested() {
        return cancellationRequested;
    }

    public CleanupStatus getCleanupStatus() {
        return cleanupStatus;
    }

    public String getObligation() {
        return obligation;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getMerchandiseTotal() {
        return merchandiseTotal;
    }

    public BigDecimal getShippingTotal() {
        return shippingTotal;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderLine> getLines() {
        return lines;
    }

    public OrderAddress getAddress() {
        return address;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public long getVersion() {
        return version;
    }

    public UUID getReserveCommandId() {
        return reserveCommandId;
    }

    public UUID getPaymentCommandId() {
        return paymentCommandId;
    }
}
