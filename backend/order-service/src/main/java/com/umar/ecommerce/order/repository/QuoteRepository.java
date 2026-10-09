package com.umar.ecommerce.order.repository;

import com.umar.ecommerce.order.entity.CustomerQuote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface QuoteRepository extends JpaRepository<CustomerQuote, UUID> {

    @Override
    @EntityGraph(attributePaths = "lines")
    Optional<CustomerQuote> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select quote from CustomerQuote quote where quote.id = :id")
    Optional<CustomerQuote> lockById(@Param("id") UUID id);
}
