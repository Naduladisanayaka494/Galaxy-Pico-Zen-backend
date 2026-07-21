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
 * Galaxy the product's own tiers (§13.1) — basic/nova/stellar and their
 * limits. {@link Subscription#getPlan()} references this row.
 *
 * <p>Not to be confused with {@link KnoxPlanCatalogue}, which is KNOX's own
 * agency pricing for billing its clients — a different business entirely.
 * Used to be a per-tenant table (one copy per tenant_<slug> schema); moved
 * here because "what a tenant pays Galaxy" is platform data, not something a
 * tenant's own schema has any business reading or writing.
 */
@Entity
@Table(name = "galaxy_plans", schema = "knox")
@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
public class GalaxyPlan {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "billing_plan")
    @Type(type = "pgsql_enum")
    private BillingPlan plan;

    /** Null for Stellar, which is custom-priced. */
    @Column(name = "monthly_price", precision = 14, scale = 2)
    private BigDecimal monthlyPrice;

    /** Null on any of these four means "unlimited" for that dimension. */
    @Column(name = "max_warehouses")
    private Integer maxWarehouses;

    @Column(name = "max_products")
    private Integer maxProducts;

    @Column(name = "max_orders_month")
    private Integer maxOrdersMonth;

    @Column(name = "max_users")
    private Integer maxUsers;
}
