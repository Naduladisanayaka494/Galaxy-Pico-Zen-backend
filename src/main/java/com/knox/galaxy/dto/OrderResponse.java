package com.knox.galaxy.dto;

import com.knox.galaxy.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A full order with its lines. The list endpoint returns the same shape with
 * {@code items} omitted — see {@code OrderService.toSummary}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private String orderCode;
    private OrderStatus status;
    private LocalDateTime orderedAt;

    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String customerAddress;

    /** Null when the user who placed it has since been deleted. */
    private String placedBy;

    private Long deliveryMethodId;
    private String deliveryMethodName;
    private Long paymentMethodId;
    private String paymentMethodName;
    private Long discountCodeId;
    private String discountCode;

    /** Sum of the line totals, before delivery and discount. */
    private BigDecimal subtotal;

    private BigDecimal deliveryCharge;
    private BigDecimal discountAmount;

    /** subtotal + deliveryCharge - discountAmount. */
    private BigDecimal total;

    private int itemCount;
    private String statusReason;

    /** Null on list responses, populated on the single-order fetch. */
    private List<OrderItemResponse> items;
}
