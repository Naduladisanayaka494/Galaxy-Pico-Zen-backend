package com.knox.galaxy.repository;

import com.knox.galaxy.model.Inventory;
import com.knox.galaxy.model.InventoryId;
import com.knox.galaxy.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, InventoryId> {

    /** All inventory rows for a given product (one per warehouse). */
    List<Inventory> findByProduct(Product product);

    /** Delete all inventory rows for a product (used on delete / reset). */
    void deleteByProduct(Product product);
}
