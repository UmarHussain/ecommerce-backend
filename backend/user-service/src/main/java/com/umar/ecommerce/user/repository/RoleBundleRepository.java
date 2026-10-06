package com.umar.ecommerce.user.repository;

import com.umar.ecommerce.user.entity.RoleBundle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleBundleRepository extends JpaRepository<RoleBundle, UUID> {

    Optional<RoleBundle> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
