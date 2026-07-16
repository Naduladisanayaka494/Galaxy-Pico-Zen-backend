package com.knox.galaxy.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;

@Entity
@Table(name = "inventory")
@IdClass(InventoryId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "on_hand", nullable = false)
    private int onHand = 0;

    @Column(nullable = false)
    private int reserved = 0;
}
