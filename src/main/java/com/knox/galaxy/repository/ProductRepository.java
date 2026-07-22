package com.knox.galaxy.repository;

import com.knox.galaxy.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByProductCode(String productCode);

    boolean existsByProductCodeIgnoreCase(String productCode);

    // ---- Paginated search (all products) ----
    Page<Product> findByNameContainingIgnoreCaseOrProductCodeContainingIgnoreCase(
            String name, String code, Pageable pageable);

    // ---- Paginated search (active-only) ----
    Page<Product> findByIsActiveAndNameContainingIgnoreCaseOrIsActiveAndProductCodeContainingIgnoreCase(
            boolean active1, String name, boolean active2, String code, Pageable pageable);

    // ---- Paginated list without search (all products) ----
    // JpaRepository.findAll(Pageable) covers this case already.

    // ---- Paginated list (active-only, no search) ----
    Page<Product> findByIsActive(boolean isActive, Pageable pageable);

    /**
     * Total on-hand stock for a product summed across all warehouses.
     * Returns 0 when no inventory rows exist.
     */
    @Query("SELECT COALESCE(SUM(i.onHand), 0) FROM Inventory i WHERE i.product.id = :productId")
    int sumOnHandByProductId(@Param("productId") Long productId);
}
