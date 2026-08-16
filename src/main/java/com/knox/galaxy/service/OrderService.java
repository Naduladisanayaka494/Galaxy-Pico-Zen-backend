package com.knox.galaxy.service;

import com.knox.galaxy.dto.*;
import com.knox.galaxy.model.*;
import com.knox.galaxy.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Order placement, status transitions and their effect on stock.
 *
 * <h2>Why orders don't write stock_movements</h2>
 * The {@code stock_movement_type} enum is only initial_stock / refill /
 * transfer, and the table's CHECK requires a destination warehouse on every
 * row — a sale has neither a matching type nor a destination. Orders therefore
 * move stock through {@code inventory.reserved}, which is what that column
 * exists for, and leave {@code stock_movements} to warehouse operations.
 *
 * <h2>Stock lifecycle</h2>
 * Each status maps to one of three stock states, and a transition undoes the
 * old state then applies the new one. That single rule covers every path,
 * including delivered → returned (stock comes back) and cancelled → processing
 * (stock is reserved again).
 *
 * <h2>Known limitation</h2>
 * {@code order_items} has no warehouse column, so an order cannot record which
 * warehouse its stock came from. Allocation walks warehouses by id and takes
 * what each can give; releases walk the same order. Adding
 * {@code order_items.warehouse_id} in a future migration would make this exact.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** What an order in a given status is doing to stock. */
    private enum StockState {
        /** Held for the order but still physically present. */
        RESERVED,
        /** Gone — shipped to the customer. */
        CONSUMED,
        /** Not held at all. */
        RELEASED
    }

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderStatusHistoryRepository statusHistoryRepository;
    @Autowired private CustomerService customerService;
    @Autowired private ProductRepository productRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DeliveryMethodRepository deliveryMethodRepository;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private DiscountCodeRepository discountCodeRepository;
    @Autowired private NotificationService notificationService;

    // ------------------------------------------------------------------ reads

    /**
     * Picks the repository variant matching which filters are present. See
     * {@link OrderRepository} for why this isn't one query with nullable
     * parameters.
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> list(OrderStatus status, String search, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        String term = (search == null || search.isBlank()) ? null : search.trim();

        Page<Order> found;
        if (status != null && term != null) {
            found = orderRepository
                    .findByStatusAndOrderCodeContainingIgnoreCaseOrStatusAndCustomerNameContainingIgnoreCaseOrderByOrderedAtDesc(
                            status, term, status, term, pageable);
        } else if (status != null) {
            found = orderRepository.findByStatusOrderByOrderedAtDesc(status, pageable);
        } else if (term != null) {
            found = orderRepository
                    .findByOrderCodeContainingIgnoreCaseOrCustomerNameContainingIgnoreCaseOrderByOrderedAtDesc(
                            term, term, pageable);
        } else {
            found = orderRepository.findAllByOrderByOrderedAtDesc(pageable);
        }
        return found.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        Order order = findOrThrow(id);
        OrderResponse response = toSummary(order);
        response.setItems(orderItemRepository.findByOrderId(id)
                .stream().map(this::toResponse).collect(Collectors.toList()));
        return response;
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> history(Long id) {
        findOrThrow(id);
        return statusHistoryRepository.findByOrderIdOrderByChangedAtAsc(id)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ----------------------------------------------------------------- writes

    @Transactional
    public OrderResponse create(OrderRequest req, String actingUsername) {
        Customer customer = resolveCustomer(req);
        User actor = actingUsername == null
                ? null
                : userRepository.findByUsernameIgnoreCase(actingUsername).orElse(null);

        Order order = new Order();
        order.setOrderCode(nextOrderCode());
        order.setCustomer(customer);
        order.setPlacedBy(actor);
        order.setStatus(OrderStatus.processing);
        order.setOrderedAt(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        DeliveryMethod delivery = resolveDeliveryMethod(req.getDeliveryMethodId());
        order.setDeliveryMethod(delivery);
        order.setPaymentMethod(resolvePaymentMethod(req.getPaymentMethodId()));
        order.setDeliveryCharge(resolveDeliveryCharge(delivery, req.getDeliveryChargeOverride()));

        // Items first — the discount is a percentage of the subtotal they produce.
        List<OrderItem> items = buildItems(req.getItems());
        BigDecimal subtotal = items.stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DiscountCode discount = resolveDiscountCode(req.getDiscountCodeId());
        order.setDiscountCode(discount);
        order.setDiscountAmount(discountAmountFor(discount, subtotal));

        order = orderRepository.save(order);
        for (OrderItem item : items) {
            item.setOrder(order);
            orderItemRepository.save(item);
        }

        applyStockState(items, StockState.RELEASED, StockState.RESERVED);
        recordHistory(order, null, OrderStatus.processing, "Order placed", actor);

        notificationService.raise(NotificationType.new_order,
                "New order " + order.getOrderCode(),
                customer.getName() + " · " + items.size() + " item(s)",
                order, null, null);

        return get(order.getId());
    }

    /**
     * Moves the order to a new status and reconciles stock with it.
     *
     * <p>Cancelling, returning or refunding needs a reason (§8.4); it is written
     * to both the order and the history row.
     */
    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatusUpdateRequest req, String actingUsername) {
        Order order = findOrThrow(id);
        OrderStatus from = order.getStatus();
        OrderStatus to = req.getStatus();

        if (from == to) {
            return get(id);
        }
        if (requiresReason(to) && (req.getReason() == null || req.getReason().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A reason is required when marking an order " + to);
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(id);
        applyStockState(items, stockStateOf(from), stockStateOf(to));

        order.setStatus(to);
        order.setStatusReason(req.getReason());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        User actor = actingUsername == null
                ? null
                : userRepository.findByUsernameIgnoreCase(actingUsername).orElse(null);
        recordHistory(order, from, to, req.getReason(), actor);

        // Delivery gets its own type so the bell can distinguish "done" from
        // the routine step-by-step progress updates.
        notificationService.raise(
                to == OrderStatus.delivered
                        ? NotificationType.order_delivered
                        : NotificationType.order_status_update,
                "Order " + order.getOrderCode() + " is now " + to,
                req.getReason(), order, null, null);

        return get(id);
    }

    // ------------------------------------------------------------------ stock

    private StockState stockStateOf(OrderStatus status) {
        switch (status) {
            case processing:
            case ready_to_ship:
            case delivering:
                return StockState.RESERVED;
            case delivered:
                return StockState.CONSUMED;
            case cancelled:
            case returned:
            case refunded:
            default:
                return StockState.RELEASED;
        }
    }

    /** Undoes {@code from}, then applies {@code to}, for every line. */
    private void applyStockState(List<OrderItem> items, StockState from, StockState to) {
        if (from == to) {
            return;
        }
        for (OrderItem item : items) {
            undo(item, from);
            apply(item, to);
        }
    }

    private void undo(OrderItem item, StockState state) {
        switch (state) {
            case RESERVED:
                shiftReserved(item, -item.getQuantity());
                break;
            case CONSUMED:
                // Stock physically returns to the shelf.
                shiftOnHand(item, item.getQuantity());
                break;
            case RELEASED:
            default:
                break;
        }
    }

    private void apply(OrderItem item, StockState state) {
        switch (state) {
            case RESERVED:
                shiftReserved(item, item.getQuantity());
                break;
            case CONSUMED:
                shiftOnHand(item, -item.getQuantity());
                break;
            case RELEASED:
            default:
                break;
        }
    }

    /**
     * Adjusts {@code reserved} across the product's warehouses.
     *
     * <p>Reserving refuses to exceed what is physically on hand — the schema's
     * {@code CHECK (reserved <= on_hand)} would otherwise fail as a raw 500.
     */
    private void shiftReserved(OrderItem item, int delta) {
        List<Inventory> rows = inventoryFor(item.getProduct());
        int remaining = Math.abs(delta);

        if (delta > 0) {
            for (Inventory row : rows) {
                if (remaining == 0) break;
                int room = row.getOnHand() - row.getReserved();
                int take = Math.min(room, remaining);
                if (take <= 0) continue;
                row.setReserved(row.getReserved() + take);
                inventoryRepository.save(row);
                remaining -= take;
            }
            if (remaining > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Only " + (Math.abs(delta) - remaining) + " unit(s) of '"
                                + item.getProduct().getName() + "' are available");
            }
        } else {
            for (Inventory row : rows) {
                if (remaining == 0) break;
                int give = Math.min(row.getReserved(), remaining);
                if (give <= 0) continue;
                row.setReserved(row.getReserved() - give);
                inventoryRepository.save(row);
                remaining -= give;
            }
            if (remaining > 0) {
                // Reservations were altered behind this order's back. Nothing to
                // undo, so log rather than fail a status change on stale bookkeeping.
                log.warn("Could not release {} reserved unit(s) of product {} — reservations no longer match",
                        remaining, item.getProduct().getId());
            }
        }
    }

    /**
     * Moves physical stock. A negative delta ships goods out, clearing the
     * matching reservation as it goes.
     */
    private void shiftOnHand(OrderItem item, int delta) {
        List<Inventory> rows = inventoryFor(item.getProduct());
        int remaining = Math.abs(delta);

        if (delta < 0) {
            for (Inventory row : rows) {
                if (remaining == 0) break;
                int take = Math.min(row.getOnHand(), remaining);
                if (take <= 0) continue;
                row.setOnHand(row.getOnHand() - take);
                // Keep reserved <= on_hand: the units leaving were the reserved ones.
                row.setReserved(Math.max(0, Math.min(row.getReserved() - take, row.getOnHand())));
                inventoryRepository.save(row);
                remaining -= take;
            }
            if (remaining > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Not enough stock of '" + item.getProduct().getName()
                                + "' to fulfil this order");
            }
        } else {
            // Returns land in the first warehouse that holds this product, or a
            // fresh row in the lowest-id warehouse when none does.
            Inventory target = rows.isEmpty() ? newInventoryRow(item.getProduct()) : rows.get(0);
            target.setOnHand(target.getOnHand() + remaining);
            inventoryRepository.save(target);
        }
    }

    /** Warehouses in a stable order, so allocation and release agree. */
    private List<Inventory> inventoryFor(Product product) {
        List<Inventory> rows = new ArrayList<>(inventoryRepository.findByProduct(product));
        rows.sort(Comparator.comparing(i -> i.getWarehouse().getId()));
        return rows;
    }

    private Inventory newInventoryRow(Product product) {
        Warehouse warehouse = warehouseRepository.findAllByOrderByNameAsc().stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No warehouse exists to return stock into"));
        Inventory fresh = new Inventory();
        fresh.setProduct(product);
        fresh.setWarehouse(warehouse);
        fresh.setOnHand(0);
        fresh.setReserved(0);
        return fresh;
    }

    // ---------------------------------------------------------------- helpers

    private Customer resolveCustomer(OrderRequest req) {
        if (req.getCustomerId() != null) {
            return customerService.findOrThrow(req.getCustomerId());
        }
        if (req.getCustomer() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "An order needs either customerId or customer details");
        }
        return customerService.upsertByPhone(req.getCustomer());
    }

    private List<OrderItem> buildItems(List<OrderRequest.OrderItemRequest> requested) {
        List<OrderItem> items = new ArrayList<>();
        for (OrderRequest.OrderItemRequest line : requested) {
            Product product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Product " + line.getProductId() + " does not exist"));

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(line.getQuantity());
            // Price override is per-order (§9.1); cost always comes from the product.
            item.setUnitPrice(line.getUnitPrice() != null
                    ? line.getUnitPrice()
                    : product.getSellingPrice());
            item.setPurchasePrice(product.getPurchasePrice());
            items.add(item);
        }
        return items;
    }

    /**
     * Order codes are ORD-001, ORD-002, … Derived from the current count and
     * bumped on collision, which covers rows deleted out of the middle.
     */
    private String nextOrderCode() {
        long next = orderRepository.count() + 1;
        for (int attempt = 0; attempt < 100; attempt++) {
            String candidate = String.format("ORD-%03d", next + attempt);
            if (orderRepository.findByOrderCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not allocate an order code");
    }

    private DeliveryMethod resolveDeliveryMethod(Long id) {
        return id == null ? null : deliveryMethodRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Delivery method " + id + " does not exist"));
    }

    private PaymentMethod resolvePaymentMethod(Long id) {
        return id == null ? null : paymentMethodRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Payment method " + id + " does not exist"));
    }

    private DiscountCode resolveDiscountCode(Long id) {
        if (id == null) {
            return null;
        }
        DiscountCode code = discountCodeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Discount code " + id + " does not exist"));
        if (!code.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Discount code '" + code.getCode() + "' is no longer active");
        }
        return code;
    }

    private BigDecimal resolveDeliveryCharge(DeliveryMethod method, BigDecimal override) {
        if (override != null) {
            return override;
        }
        return method == null ? BigDecimal.ZERO : method.getCharge();
    }

    /** Never more than the subtotal — the DB requires discount_amount >= 0. */
    private BigDecimal discountAmountFor(DiscountCode code, BigDecimal subtotal) {
        if (code == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = code.getKind() == DiscountType.percentage
                ? subtotal.multiply(code.getValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : code.getValue();
        return amount.min(subtotal);
    }

    private boolean requiresReason(OrderStatus status) {
        return status == OrderStatus.cancelled
                || status == OrderStatus.returned
                || status == OrderStatus.refunded;
    }

    private void recordHistory(Order order, OrderStatus from, OrderStatus to,
                               String reason, User actor) {
        OrderStatusHistory entry = new OrderStatusHistory();
        entry.setOrder(order);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setReason(reason);
        entry.setChangedBy(actor);
        entry.setChangedAt(LocalDateTime.now());
        statusHistoryRepository.save(entry);
    }

    private Order findOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Order " + id + " not found"));
    }

    // ------------------------------------------------------------------ mapping

    /** The list shape: totals and counts, no line items. */
    private OrderResponse toSummary(Order o) {
        List<OrderItem> items = orderItemRepository.findByOrderId(o.getId());
        BigDecimal subtotal = items.stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = subtotal
                .add(o.getDeliveryCharge() == null ? BigDecimal.ZERO : o.getDeliveryCharge())
                .subtract(o.getDiscountAmount() == null ? BigDecimal.ZERO : o.getDiscountAmount());

        Customer c = o.getCustomer();
        User actor = o.getPlacedBy();
        return new OrderResponse(
                o.getId(), o.getOrderCode(), o.getStatus(), o.getOrderedAt(),
                c == null ? null : c.getId(),
                c == null ? null : c.getName(),
                c == null ? null : c.getPhone(),
                c == null ? null : c.getAddress(),
                actor == null ? null : actor.getFirstName() + " " + actor.getLastName(),
                o.getDeliveryMethod() == null ? null : o.getDeliveryMethod().getId(),
                o.getDeliveryMethod() == null ? null : o.getDeliveryMethod().getName(),
                o.getPaymentMethod() == null ? null : o.getPaymentMethod().getId(),
                o.getPaymentMethod() == null ? null : o.getPaymentMethod().getName(),
                o.getDiscountCode() == null ? null : o.getDiscountCode().getId(),
                o.getDiscountCode() == null ? null : o.getDiscountCode().getCode(),
                subtotal, o.getDeliveryCharge(), o.getDiscountAmount(), total,
                items.size(), o.getStatusReason(), null);
    }

    private OrderItemResponse toResponse(OrderItem i) {
        Product p = i.getProduct();
        return new OrderItemResponse(
                i.getId(), p.getId(), p.getProductCode(), p.getName(),
                i.getQuantity(), i.getUnitPrice(), i.getPurchasePrice(),
                i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())));
    }

    private OrderStatusHistoryResponse toResponse(OrderStatusHistory h) {
        User actor = h.getChangedBy();
        return new OrderStatusHistoryResponse(
                h.getId(), h.getFromStatus(), h.getToStatus(), h.getReason(),
                actor == null ? null : actor.getFirstName() + " " + actor.getLastName(),
                h.getChangedAt());
    }
}
