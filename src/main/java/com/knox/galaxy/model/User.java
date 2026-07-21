package com.knox.galaxy.model;

import com.vladmihalcea.hibernate.type.basic.PostgreSQLEnumType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // EAGER: role is needed on every authenticated request (Spring Security
    // authority derivation in CustomUserDetailsService) — LAZY would risk a
    // LazyInitializationException outside whatever transaction loaded the User.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    private String phone;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "commission_enabled", nullable = false)
    private boolean commissionEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_method", columnDefinition = "commission_method")
    @Type(type = "pgsql_enum")
    private CommissionMethod commissionMethod;

    @Column(name = "commission_percent", precision = 6, scale = 3)
    private BigDecimal commissionPercent;

    @Column(name = "commission_unit_amount", precision = 14, scale = 2)
    private BigDecimal commissionUnitAmount;

    @Column(name = "commission_min_units")
    private Integer commissionMinUnits;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
