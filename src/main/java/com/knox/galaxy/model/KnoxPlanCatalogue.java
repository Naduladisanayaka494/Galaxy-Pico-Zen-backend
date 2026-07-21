package com.knox.galaxy.model;

import com.vladmihalcea.hibernate.type.basic.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.math.BigDecimal;

/**
 * KNOX's own pricing (§16.5) — maps knox.plans, whose PK is the plan enum.
 *
 * <p>Named ...Catalogue to avoid colliding with {@link KnoxPlan}, the enum.
 * Not to be confused with {@link GalaxyPlan}, which is the Galaxy product's
 * own basic/nova/stellar tiers — a different business entirely.
 */
@Entity
@Table(name = "plans", schema = "knox")
@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
public class KnoxPlanCatalogue {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "knox_plan")
    @Type(type = "pgsql_enum")
    private KnoxPlan plan;

    /** Per period. Null for the unlimited plan, which bills per order. */
    @Column(name = "subscription_fee", precision = 14, scale = 2)
    private BigDecimal subscriptionFee;

    /** Unlimited plan only (Rs. 7/order). */
    @Column(name = "per_order_fee", precision = 14, scale = 2)
    private BigDecimal perOrderFee;

    @Column(name = "setup_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal setupFee;

    @Column(name = "is_yearly", nullable = false)
    private boolean isYearly = false;
}
