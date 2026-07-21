package com.knox.galaxy.repository;

import com.knox.galaxy.model.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Short> {
}
