package com.knox.galaxy.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;

/**
 * One image slot for a product. Up to 5 allowed (position 1-5).
 * Exactly one row per product may have is_default=true — enforced by
 * the uq_product_default_image partial unique index in V1__init.sql.
 */
@Entity
@Table(name = "product_images")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private Short position;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;
}
