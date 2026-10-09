package com.umar.ecommerce.cart.repository;

import com.umar.ecommerce.cart.entity.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Cart c where c.ownerIssuer = :issuer and c.ownerSubject = :subject")
    Optional<Cart> readByOwner(@Param("issuer") String issuer, @Param("subject") String subject);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.ownerIssuer = :issuer and c.ownerSubject = :subject")
    Optional<Cart> lockByOwner(@Param("issuer") String issuer, @Param("subject") String subject);
}
