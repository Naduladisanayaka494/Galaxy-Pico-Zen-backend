package com.knox.galaxy.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Places an order.
 *
 * <p>Give either {@code customerId} for an existing customer, or {@code customer}
 * to create one inline — the Place Order screen collects the details in the same
 * form, and customers are keyed by phone, so an inline block whose phone already
 * exists updates that customer rather than failing on the unique index.
 *
 * <p>Delivery charge and discount amount are <em>derived</em> from the chosen
 * method and code and snapshotted onto the order; they are not accepted from the
 * client, so a tampered payload can't change what the customer is billed.
 */
@Data
public class OrderRequest {

    private Long customerId;

    @Valid
    private CustomerRequest customer;

    @NotEmpty(message = "An order needs at least one item")
    @Valid
    private List<OrderItemRequest> items;

    private Long deliveryMethodId;
    private Long paymentMethodId;

    /** Optional; the code's own kind and value decide the discount amount. */
    private Long discountCodeId;

    /**
     * Overrides the delivery method's default charge when the courier quoted
     * something different. Null keeps the method's configured charge.
     */
    @DecimalMin(value = "0.00", message = "Delivery charge must be >= 0")
    private BigDecimal deliveryChargeOverride;

    @Data
    public static class OrderItemRequest {

        @NotNull(message = "Product is required")
        private Long productId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        /**
         * Per-order price override (§9.1). Null falls back to the product's
         * current selling price. The cost snapshot always comes from the
         * product, never the client.
         */
        @DecimalMin(value = "0.00", message = "Unit price must be >= 0")
        private BigDecimal unitPrice;
    }
}
