package com.knox.galaxy.repository;

import com.knox.galaxy.model.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    List<PaymentMethod> findAllByIsActiveOrderByNameAsc(boolean isActive);

    List<PaymentMethod> findAllByOrderByNameAsc();

    Optional<PaymentMethod> findByNameIgnoreCase(String name);
}
