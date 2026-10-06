package com.umar.ecommerce.user.repository;

import com.umar.ecommerce.user.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByIssuerAndSubject(String issuer, String subject);

    Optional<AppUser> findByEmailSnapshotIgnoreCase(String emailSnapshot);
}
