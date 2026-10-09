package com.umar.ecommerce.payment.repository;

import com.umar.ecommerce.payment.entity.SimulatorDue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimulatorDueRepository extends JpaRepository<SimulatorDue, UUID> {

    @Query("""
            select due.id from SimulatorDue due
            where due.completed = false and due.dueAt <= :now
            order by due.dueAt asc, due.id asc
            """)
    List<UUID> findDueIds(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select due from SimulatorDue due where due.id = :id")
    Optional<SimulatorDue> lockById(@Param("id") UUID id);
}
