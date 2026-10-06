package com.umar.ecommerce.user.repository;

import com.umar.ecommerce.user.entity.AccessAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccessAuditRepository extends JpaRepository<AccessAudit, UUID> {
}
