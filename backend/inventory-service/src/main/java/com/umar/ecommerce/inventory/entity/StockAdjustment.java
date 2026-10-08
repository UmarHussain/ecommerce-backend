package com.umar.ecommerce.inventory.entity;

import com.umar.ecommerce.inventory.domain.OperationScope;
import com.umar.ecommerce.inventory.domain.ReasonCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_adjustment")
public class StockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_item_id", nullable = false, updatable = false)
    private StockItem stockItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, updatable = false, length = 32)
    private OperationScope operationType;

    @Column(name = "delta", nullable = false, updatable = false)
    private int delta;

    @Column(name = "before_on_hand", nullable = false, updatable = false)
    private int beforeOnHand;

    @Column(name = "after_on_hand", nullable = false, updatable = false)
    private int afterOnHand;

    @Column(name = "reserved_snapshot", nullable = false, updatable = false)
    private int reservedSnapshot;

    @Column(name = "resulting_version", nullable = false, updatable = false)
    private long resultingVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, updatable = false, length = 40)
    private ReasonCode reasonCode;

    @Column(name = "note", updatable = false, length = 500)
    private String note;

    @Column(name = "reference_text", updatable = false, length = 120)
    private String referenceText;

    @Column(name = "actor_issuer", nullable = false, updatable = false, length = 300)
    private String actorIssuer;

    @Column(name = "actor_subject", nullable = false, updatable = false, length = 200)
    private String actorSubject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockAdjustment() {
    }

    public static StockAdjustment record(
            StockItem stockItem,
            OperationScope operationType,
            int delta,
            int beforeOnHand,
            int afterOnHand,
            int reservedSnapshot,
            long resultingVersion,
            ReasonCode reasonCode,
            String note,
            String referenceText,
            String actorIssuer,
            String actorSubject
    ) {
        StockAdjustment adjustment = new StockAdjustment();
        adjustment.stockItem = stockItem;
        adjustment.operationType = operationType;
        adjustment.delta = delta;
        adjustment.beforeOnHand = beforeOnHand;
        adjustment.afterOnHand = afterOnHand;
        adjustment.reservedSnapshot = reservedSnapshot;
        adjustment.resultingVersion = resultingVersion;
        adjustment.reasonCode = reasonCode;
        adjustment.note = note;
        adjustment.referenceText = referenceText;
        adjustment.actorIssuer = actorIssuer;
        adjustment.actorSubject = actorSubject;
        return adjustment;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public StockItem getStockItem() {
        return stockItem;
    }

    public OperationScope getOperationType() {
        return operationType;
    }

    public int getDelta() {
        return delta;
    }

    public int getBeforeOnHand() {
        return beforeOnHand;
    }

    public int getAfterOnHand() {
        return afterOnHand;
    }

    public int getReservedSnapshot() {
        return reservedSnapshot;
    }

    public long getResultingVersion() {
        return resultingVersion;
    }

    public ReasonCode getReasonCode() {
        return reasonCode;
    }

    public String getNote() {
        return note;
    }

    public String getReferenceText() {
        return referenceText;
    }

    public String getActorIssuer() {
        return actorIssuer;
    }

    public String getActorSubject() {
        return actorSubject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
