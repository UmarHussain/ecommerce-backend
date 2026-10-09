package com.umar.ecommerce.payment.repository;

import com.umar.ecommerce.payment.entity.PaymentAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from PaymentAttempt attempt where attempt.operationId = :id")
    Optional<PaymentAttempt> lockById(@Param("id") UUID id);
}
