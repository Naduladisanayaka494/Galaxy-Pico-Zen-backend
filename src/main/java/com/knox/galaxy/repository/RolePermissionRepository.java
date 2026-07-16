package com.knox.galaxy.repository;

import com.knox.galaxy.model.RolePermission;
import com.knox.galaxy.model.RolePermissionId;
import com.knox.galaxy.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
    List<RolePermission> findByRole(UserRole role);
}
