package com.knox.galaxy.repository;

import com.knox.galaxy.model.DeliveryMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryMethodRepository extends JpaRepository<DeliveryMethod, Long> {

    List<DeliveryMethod> findAllByIsActiveOrderByNameAsc(boolean isActive);

    List<DeliveryMethod> findAllByOrderByNameAsc();

    Optional<DeliveryMethod> findByNameIgnoreCase(String name);
}
