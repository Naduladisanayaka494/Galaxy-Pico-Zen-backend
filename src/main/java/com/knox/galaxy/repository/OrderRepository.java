package com.knox.galaxy.repository;

import com.knox.galaxy.model.Order;
import com.knox.galaxy.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Manage Orders reads.
 *
 * <p>The four list variants exist instead of one query with nullable
 * parameters. {@code Order.status} is a Postgres enum bound through
 * {@code PostgreSQLEnumType}, which sends a null as {@code Types.OTHER}; a
 * {@code (:status IS NULL OR ...)} clause then leaves Postgres unable to infer
 * the parameter's type and the whole query fails. Every parameter here is
 * always non-null, which sidesteps that entirely — the same shape
 * {@link ProductRepository} already uses for its optional filters.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Order> {

    Optional<Order> findByOrderCode(String orderCode);

    // ---- No filters ----
    Page<Order> findAllByOrderByOrderedAtDesc(Pageable pageable);

    // ---- Status only ----
    Page<Order> findByStatusOrderByOrderedAtDesc(OrderStatus status, Pageable pageable);

    // ---- Search only (order code or customer name) ----
    Page<Order> findByOrderCodeContainingIgnoreCaseOrCustomerNameContainingIgnoreCaseOrderByOrderedAtDesc(
            String orderCode, String customerName, Pageable pageable);

    // ---- Status + search. The status is repeated because the OR distributes
    //      across both text columns, exactly as in ProductRepository. ----
    Page<Order> findByStatusAndOrderCodeContainingIgnoreCaseOrStatusAndCustomerNameContainingIgnoreCaseOrderByOrderedAtDesc(
            OrderStatus statusForCode, String orderCode,
            OrderStatus statusForName, String customerName,
            Pageable pageable);

    long countByCustomerId(Long customerId);

    /** Orders placed in a window — measures usage against the plan's monthly cap. */
    long countByOrderedAtBetween(java.time.LocalDateTime from, java.time.LocalDateTime to);
}
