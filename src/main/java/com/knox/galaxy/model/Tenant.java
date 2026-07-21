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
 * Platform routing record: which schema serves this business.
 *
 * <p>Schema-qualified to {@code knox} so it resolves no matter which tenant's
 * search_path the current connection carries.
 */
@Entity
@Table(name = "tenants", schema = "knox")
@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "schema_name", nullable = false, unique = true)
    private String schemaName;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "tenant_status")
    @Type(type = "pgsql_enum")
    private TenantStatus status = TenantStatus.provisioning;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
