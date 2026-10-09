package com.umar.ecommerce.order.repository;

import com.umar.ecommerce.order.entity.CustomerOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select customerOrder from CustomerOrder customerOrder where customerOrder.id = :id")
    Optional<CustomerOrder> lockById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"lines", "address"})
    @Query("""
            select customerOrder from CustomerOrder customerOrder
            where customerOrder.ownerIssuer = :issuer and customerOrder.ownerSubject = :subject
            and customerOrder.id = :id
            """)
    Optional<CustomerOrder> findOwned(
            @Param("issuer") String issuer,
            @Param("subject") String subject,
            @Param("id") UUID id
    );

    Page<CustomerOrder> findByOwnerIssuerAndOwnerSubject(String ownerIssuer, String ownerSubject, Pageable pageable);
}
