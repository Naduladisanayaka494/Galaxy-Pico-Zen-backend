package com.knox.galaxy.repository;

import com.knox.galaxy.model.Order;
import com.knox.galaxy.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrder(Order order);

    List<OrderItem> findByOrderId(Long orderId);

    long countByOrderId(Long orderId);
}
