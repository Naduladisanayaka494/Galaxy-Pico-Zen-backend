package com.knox.galaxy.repository;

import com.knox.galaxy.model.BusinessSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Single-row table: the schema pins the primary key to 1 via
 * {@code CHECK (id = 1)}, so there is never more than one settings row per
 * tenant. Use {@code findById(BusinessSettings.SINGLETON_ID)}.
 */
@Repository
public interface BusinessSettingsRepository extends JpaRepository<BusinessSettings, Short> {
}
