package com.knox.galaxy.model;

import com.vladmihalcea.hibernate.type.basic.PostgreSQLEnumType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "delivery_methods")
@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
public class DeliveryMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /**
     * The default charge, and the fallback for any region with no rate row.
     * Read as LKR when {@link #chargeKind} is {@code fixed}, or as a percent
     * of the order's items subtotal when it is {@code percentage}.
     */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal charge = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_kind", nullable = false, columnDefinition = "delivery_charge_kind")
    @Type(type = "pgsql_enum")
    private DeliveryChargeKind chargeKind = DeliveryChargeKind.fixed;

    /**
     * {@code flat} ignores {@link DeliveryMethodRate} entirely; the other two
     * say which region kind to match an order against.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rate_scope", nullable = false, columnDefinition = "delivery_region_type")
    @Type(type = "pgsql_enum")
    private DeliveryRegionType rateScope = DeliveryRegionType.flat;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
