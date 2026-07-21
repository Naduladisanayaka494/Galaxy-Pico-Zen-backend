package com.knox.galaxy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

/**
 * Per-tenant, not shared — each business can rename, add, or remove roles.
 * A table rather than an enum on purpose: users.role_id and
 * role_permissions.role_id both FK here, and an enum can't be a join target.
 *
 * <p>'owner' is seeded with isSystem=true and is meant to stay that way — the
 * one role every tenant is guaranteed to have. Nothing currently enforces
 * that at the DB or service layer; there's no role-management endpoint yet
 * for it to guard against.
 */
@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem = false;
}
