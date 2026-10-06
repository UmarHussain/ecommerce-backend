package com.umar.ecommerce.user.repository;

import com.umar.ecommerce.user.domain.OperationStatus;
import com.umar.ecommerce.user.entity.IdentityOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdentityOperationRepository extends JpaRepository<IdentityOperation, UUID> {

    Optional<IdentityOperation> findByIdempotencyKey(String idempotencyKey);

    List<IdentityOperation> findByStatusAndNextAttemptAtLessThanEqual(
            OperationStatus status,
            Instant nextAttemptAt
    );
}
