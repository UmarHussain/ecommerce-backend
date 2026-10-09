package com.umar.ecommerce.order.repository;

import com.umar.ecommerce.order.entity.CheckoutRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CheckoutRequestRepository extends JpaRepository<CheckoutRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request from CheckoutRequest request
            where request.ownerIssuer = :issuer and request.ownerSubject = :subject
            and request.idempotencyKey = :key
            """)
    Optional<CheckoutRequest> lockByOwnerAndKey(
            @Param("issuer") String issuer,
            @Param("subject") String subject,
            @Param("key") String key
    );

    Optional<CheckoutRequest> findByOwnerIssuerAndOwnerSubjectAndIdempotencyKey(
            String ownerIssuer,
            String ownerSubject,
            String idempotencyKey
    );
}
