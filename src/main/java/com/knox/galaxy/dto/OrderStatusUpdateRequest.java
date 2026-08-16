package com.knox.galaxy.dto;

import com.knox.galaxy.model.OrderStatus;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Changes an order's status.
 *
 * <p>A reason is required when moving to cancelled, returned or refunded (§8.4)
 * — those are the transitions someone will later need explained.
 */
@Data
public class OrderStatusUpdateRequest {

    @NotNull(message = "A target status is required")
    private OrderStatus status;

    @Size(max = 1000)
    private String reason;
}
