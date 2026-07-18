package com.knox.galaxy.repository;

import com.knox.galaxy.model.Subscription;
import com.knox.galaxy.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByTenantIdAndStatusNot(Long tenantId, SubscriptionStatus status);
}
