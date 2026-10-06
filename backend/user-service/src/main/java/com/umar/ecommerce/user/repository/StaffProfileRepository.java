package com.umar.ecommerce.user.repository;

import com.umar.ecommerce.user.entity.StaffProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, UUID> {
}
