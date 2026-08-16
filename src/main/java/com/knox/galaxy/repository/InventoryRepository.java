package com.knox.galaxy.repository;

import com.knox.galaxy.model.Inventory;
import com.knox.galaxy.model.InventoryId;
import com.knox.galaxy.model.Product;
import com.knox.galaxy.model.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, InventoryId> {

    /** All inventory rows for a given product (one per warehouse). */
    List<Inventory> findByProduct(Product product);

    /** Delete all inventory rows for a product (used on delete / reset). */
    void deleteByProduct(Product product);

    List<Inventory> findByWarehouse(Warehouse warehouse);

    Optional<Inventory> findByProductAndWarehouse(Product product, Warehouse warehouse);

    long countByWarehouse(Warehouse warehouse);

    /** Units currently stored in a warehouse — the numerator of its fill %. */
    @Query("SELECT COALESCE(SUM(i.onHand), 0) FROM Inventory i WHERE i.warehouse.id = :warehouseId")
    int sumOnHandByWarehouseId(@Param("warehouseId") Long warehouseId);
}
