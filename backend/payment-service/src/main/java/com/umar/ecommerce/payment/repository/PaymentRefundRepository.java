package com.umar.ecommerce.payment.repository;

import com.umar.ecommerce.payment.entity.PaymentRefund;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select refund from PaymentRefund refund where refund.operationId = :id")
    Optional<PaymentRefund> lockById(@Param("id") UUID id);

    @Query("select refund from PaymentRefund refund where refund.chargeOperationId = :chargeId and refund.applied = true")
    Optional<PaymentRefund> findApplied(@Param("chargeId") UUID chargeId);
}
