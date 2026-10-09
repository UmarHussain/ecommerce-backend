package com.umar.ecommerce.order.repository;

import com.umar.ecommerce.order.entity.OrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, UUID> {
}
