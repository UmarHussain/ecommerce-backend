package com.umar.ecommerce.user.repository;

import com.umar.ecommerce.user.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    List<CustomerAddress> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<CustomerAddress> findByIdAndUserId(UUID id, UUID userId);
}
