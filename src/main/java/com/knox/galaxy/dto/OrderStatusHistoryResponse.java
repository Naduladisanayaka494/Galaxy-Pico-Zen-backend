package com.knox.galaxy.dto;

import com.knox.galaxy.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusHistoryResponse {
    private Long id;

    /** Null on the row recorded when the order was created. */
    private OrderStatus fromStatus;

    private OrderStatus toStatus;
    private String reason;
    private String changedBy;
    private LocalDateTime changedAt;
}
