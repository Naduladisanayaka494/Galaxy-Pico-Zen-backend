package com.knox.galaxy.model;

import com.vladmihalcea.hibernate.type.basic.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A KNOX agency client — the CRM/billing record (§16).
 *
 * <p>Distinct from {@link Tenant}: a client is someone KNOX bills, a tenant is
 * a provisioned Galaxy schema. A client may exist without a tenant (sold, not
 * yet set up); {@code Tenant.clientId} links the two when they are.
 */
@Entity
@Table(name = "clients", schema = "knox")
@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "contact_person")
    private String contactPerson;

    private String phone;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "knox_plan")
    @Type(type = "pgsql_enum")
    private KnoxPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "knox_status")
    @Type(type = "pgsql_enum")
    private KnoxStatus status = KnoxStatus.trial;

    /** Drives both the 7-day trial window and the renewal schedule. */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "setup_option", nullable = false, columnDefinition = "knox_setup_option")
    @Type(type = "pgsql_enum")
    private KnoxSetupOption setupOption = KnoxSetupOption.full;

    @Column(name = "on_trial", nullable = false)
    private boolean onTrial = true;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
