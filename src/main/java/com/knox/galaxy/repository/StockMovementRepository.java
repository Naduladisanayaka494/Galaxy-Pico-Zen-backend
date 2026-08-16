package com.knox.galaxy.repository;

import com.knox.galaxy.model.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Page<StockMovement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    /** Movements touching a warehouse in either direction. */
    Page<StockMovement> findByWarehouseFromIdOrWarehouseToIdOrderByCreatedAtDesc(
            Long warehouseFromId, Long warehouseToId, Pageable pageable);
}
