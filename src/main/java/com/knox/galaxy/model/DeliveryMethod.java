package com.knox.galaxy.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "delivery_methods")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal charge = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
