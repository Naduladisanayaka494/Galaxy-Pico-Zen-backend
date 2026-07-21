package com.knox.galaxy.repository;

import com.knox.galaxy.model.BillingPlan;
import com.knox.galaxy.model.GalaxyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GalaxyPlanRepository extends JpaRepository<GalaxyPlan, BillingPlan> {
}
