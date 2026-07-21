package com.knox.galaxy.model;

import com.vladmihalcea.hibernate.type.basic.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Platform identity directory. Credentials live here rather than in
 * {@code tenant_<slug>.users} because login has to resolve which tenant a
 * person belongs to before any tenant schema can be selected.
 *
 * <p>{@link #localUserId} points at the matching {@link User} row inside the
 * tenant's own schema, which still owns role, commission and profile data.
 */
@Entity
@Table(name = "tenant_users", schema = "knox")
@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
public class TenantUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "local_user_id")
    private Long localUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "tenant_user_status")
    @Type(type = "pgsql_enum")
    private TenantUserStatus status = TenantUserStatus.active;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
