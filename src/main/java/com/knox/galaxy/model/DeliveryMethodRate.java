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
 * One region's delivery charge for one method — "Kandy district: 450".
 *
 * <p>The method's own {@code charge} stays the fallback for regions with no
 * row here, and {@code regionType} is stored per row rather than read off the
 * method's scope so a method can hold both a province grid and a district grid
 * while the user flips between them in Settings.
 */
@Entity
@Table(name = "delivery_method_rates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
public class DeliveryMethodRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_method_id", nullable = false)
    private DeliveryMethod deliveryMethod;

    /** Never {@code flat} — the DB rejects it. */
    @Enumerated(EnumType.STRING)
    @Column(name = "region_type", nullable = false, columnDefinition = "delivery_region_type")
    @Type(type = "pgsql_enum")
    private DeliveryRegionType regionType;

    /** Province or district name as the client sent it, e.g. "North Western". */
    @Column(name = "region_name", nullable = false)
    private String regionName;

    /** Read as LKR or as a percent, per the parent method's charge kind. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal charge = BigDecimal.ZERO;
}
