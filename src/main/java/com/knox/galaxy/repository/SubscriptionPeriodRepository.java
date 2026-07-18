package com.knox.galaxy.repository;

import com.knox.galaxy.model.SubscriptionPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPeriodRepository extends JpaRepository<SubscriptionPeriod, Long> {
    List<SubscriptionPeriod> findByClientIdOrderByPeriodStartAsc(Long clientId);
    List<SubscriptionPeriod> findByClientIdIn(List<Long> clientIds);
    Optional<SubscriptionPeriod> findByClientIdAndPeriodStart(Long clientId, LocalDate periodStart);
}
