package com.umar.ecommerce.inventory.repository;

import com.umar.ecommerce.inventory.domain.OperationScope;
import com.umar.ecommerce.inventory.entity.InventoryCommand;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InventoryCommandRepository extends JpaRepository<InventoryCommand, UUID> {

    Optional<InventoryCommand> findByActorIssuerAndActorSubjectAndOperationScopeAndIdempotencyKey(
            String actorIssuer,
            String actorSubject,
            OperationScope operationScope,
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select command from InventoryCommand command
            where command.actorIssuer = :issuer
              and command.actorSubject = :subject
              and command.operationScope = :scope
              and command.idempotencyKey = :idempotencyKey
            """)
    Optional<InventoryCommand> lockByIdentity(
            @Param("issuer") String issuer,
            @Param("subject") String subject,
            @Param("scope") OperationScope scope,
            @Param("idempotencyKey") String idempotencyKey
    );
}
