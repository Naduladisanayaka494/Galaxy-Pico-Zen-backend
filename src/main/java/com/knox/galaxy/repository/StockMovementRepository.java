package com.knox.galaxy.repository;

import com.knox.galaxy.model.StockMovement;
import com.knox.galaxy.model.StockMovementType;
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

    // One kind of movement at a time — the Warehouses page wants transfers
    // only, and paging through every refill to find them does not scale.

    Page<StockMovement> findByTypeOrderByCreatedAtDesc(StockMovementType type, Pageable pageable);

    Page<StockMovement> findByTypeAndProductIdOrderByCreatedAtDesc(
            StockMovementType type, Long productId, Pageable pageable);

    Page<StockMovement> findByTypeAndWarehouseFromIdOrTypeAndWarehouseToIdOrderByCreatedAtDesc(
            StockMovementType typeFrom, Long warehouseFromId,
            StockMovementType typeTo, Long warehouseToId, Pageable pageable);
}
