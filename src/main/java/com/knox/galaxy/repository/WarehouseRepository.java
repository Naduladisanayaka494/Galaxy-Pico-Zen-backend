package com.knox.galaxy.repository;

import com.knox.galaxy.model.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    Optional<Warehouse> findByCode(String code);

    /** Returns all active warehouses sorted alphabetically — used by Add/Edit Product form. */
    List<Warehouse> findAllByIsActiveOrderByNameAsc(boolean isActive);
}
