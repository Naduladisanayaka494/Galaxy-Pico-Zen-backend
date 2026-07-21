package com.knox.galaxy.repository;

import com.knox.galaxy.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySlug(String slug);
    Optional<Tenant> findByClientId(Long clientId);
    List<Tenant> findByClientIdIn(List<Long> clientIds);
    boolean existsBySlug(String slug);
    boolean existsBySchemaName(String schemaName);
}
