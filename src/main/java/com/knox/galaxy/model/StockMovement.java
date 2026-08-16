package com.knox.galaxy.model;

import com.vladmihalcea.hibernate.type.basic.PostgreSQLEnumType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable audit row for every stock change (§5.5 / §10.5).
 *
 * <p>The schema constrains which warehouse columns each type requires:
 * {@code transfer} needs both from and to, while {@code initial_stock} and
 * {@code refill} need only to. {@code InventoryService} checks that before
 * insert so a bad combination reads as a 400 rather than a constraint 500.
 */
@Entity
@Table(name = "stock_movements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "stock_movement_type")
    @Type(type = "pgsql_enum")
    private StockMovementType type;

    @Column(nullable = false)
    private int quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_from_id")
    private Warehouse warehouseFrom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_to_id")
    private Warehouse warehouseTo;

    /** Price snapshots at the time of the event, for later cost reporting. */
    @Column(name = "purchase_price", precision = 14, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "selling_price", precision = 14, scale = 2)
    private BigDecimal sellingPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
