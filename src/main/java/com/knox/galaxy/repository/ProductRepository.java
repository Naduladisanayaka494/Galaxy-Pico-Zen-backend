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

    /** Used when naming a copy, to find the first free "<name> copy N". */
    boolean existsByNameIgnoreCase(String name);

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

    /** How many products reference a category — guards category deletion. */
    long countByCategoryId(Long categoryId);

    /**
     * Total on-hand stock for a product summed across all warehouses.
     * Returns 0 when no inventory rows exist.
     */
    @Query("SELECT COALESCE(SUM(i.onHand), 0) FROM Inventory i WHERE i.product.id = :productId")
    int sumOnHandByProductId(@Param("productId") Long productId);

    /**
     * Units of this product actually sold, all time. Returns 0 when it has
     * never sold.
     *
     * <p>Delivered lines only, which is what "sold" means everywhere else in
     * this codebase — see ReportRepository's dead-stock and top-products
     * queries. An order still in flight has not earned anything yet, and a
     * cancelled, returned or refunded one never will, so counting either here
     * would put a different number on Item Stock than the Stock Report shows
     * for the same product.
     *
     * <p>Native, like every other status comparison here: {@code status} is a
     * PostgreSQL enum type, and a JPQL enum literal binds as a varchar against
     * it, which Postgres rejects. A string literal in SQL casts cleanly.
     */
    @Query(value = "SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi "
            + "JOIN orders o ON o.id = oi.order_id "
            + "WHERE oi.product_id = :productId AND o.status = 'delivered'",
            nativeQuery = true)
    int sumDeliveredQuantityByProductId(@Param("productId") Long productId);
}
