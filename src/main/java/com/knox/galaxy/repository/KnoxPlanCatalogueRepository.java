package com.knox.galaxy.repository;

import com.knox.galaxy.model.KnoxPlan;
import com.knox.galaxy.model.KnoxPlanCatalogue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KnoxPlanCatalogueRepository extends JpaRepository<KnoxPlanCatalogue, KnoxPlan> {
}
