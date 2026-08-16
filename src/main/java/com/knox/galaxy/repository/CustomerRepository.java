package com.knox.galaxy.repository;

import com.knox.galaxy.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Customers are identified by phone number (§10.3) — it carries the UNIQUE index. */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByPhone(String phone);

    List<Customer> findAllByOrderByNameAsc();

    Page<Customer> findByNameContainingIgnoreCaseOrPhoneContainingIgnoreCase(
            String name, String phone, Pageable pageable);
}
