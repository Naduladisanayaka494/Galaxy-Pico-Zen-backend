package com.knox.galaxy.repository;

import com.knox.galaxy.model.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {

    List<DiscountCode> findAllByIsActiveOrderByCodeAsc(boolean isActive);

    List<DiscountCode> findAllByOrderByCodeAsc();

    Optional<DiscountCode> findByCodeIgnoreCase(String code);
}
