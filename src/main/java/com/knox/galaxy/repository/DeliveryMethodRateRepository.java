package com.knox.galaxy.repository;

import com.knox.galaxy.model.DeliveryMethodRate;
import com.knox.galaxy.model.DeliveryRegionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryMethodRateRepository extends JpaRepository<DeliveryMethodRate, Long> {

    List<DeliveryMethodRate> findByDeliveryMethodIdOrderByRegionNameAsc(Long deliveryMethodId);

    /** One query for the whole list screen, instead of one per method. */
    List<DeliveryMethodRate> findByDeliveryMethodIdIn(Collection<Long> deliveryMethodIds);

    Optional<DeliveryMethodRate> findByDeliveryMethodIdAndRegionTypeAndRegionNameIgnoreCase(
            Long deliveryMethodId, DeliveryRegionType regionType, String regionName);

    void deleteByDeliveryMethodId(Long deliveryMethodId);
}
