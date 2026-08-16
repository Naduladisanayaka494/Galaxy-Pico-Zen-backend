package com.knox.galaxy.service;

import com.knox.galaxy.dto.InventoryResponse;
import com.knox.galaxy.dto.StockAdjustmentRequest;
import com.knox.galaxy.dto.StockMovementResponse;
import com.knox.galaxy.model.*;
import com.knox.galaxy.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Per-warehouse stock levels and the movement history behind them.
 *
 * <p>Every change goes through {@link #adjust}: it updates {@code inventory}
 * and appends the matching {@code stock_movements} row in one transaction, so
 * the audit log can never drift from the balances it explains.
 */
@Service
public class InventoryService {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private UserRepository userRepository;

    /** Filters are optional and combine: both null returns everything. */
    @Transactional(readOnly = true)
    public List<InventoryResponse> list(Long warehouseId, Long productId) {
        List<Inventory> rows;
        if (warehouseId != null) {
            rows = inventoryRepository.findByWarehouse(findWarehouse(warehouseId));
            if (productId != null) {
                rows = rows.stream()
                        .filter(i -> i.getProduct().getId().equals(productId))
                        .collect(Collectors.toList());
            }
        } else if (productId != null) {
            rows = inventoryRepository.findByProduct(findProduct(productId));
        } else {
            rows = inventoryRepository.findAll();
        }
        return rows.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> movements(Long warehouseId, Long productId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<StockMovement> found;
        if (productId != null) {
            found = stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable);
        } else if (warehouseId != null) {
            found = stockMovementRepository
                    .findByWarehouseFromIdOrWarehouseToIdOrderByCreatedAtDesc(warehouseId, warehouseId, pageable);
        } else {
            found = stockMovementRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return found.map(this::toResponse);
    }

    /**
     * Applies a stock movement and records it.
     *
     * <p>Transfers move units between two warehouses; initial_stock and refill
     * add them to one. The source balance is checked first — the DB's
     * {@code on_hand >= 0} check would otherwise surface as a raw 500.
     */
    @Transactional
    public StockMovementResponse adjust(StockAdjustmentRequest req, String actingUsername) {
        Product product = findProduct(req.getProductId());
        int quantity = req.getQuantity();

        if (req.getWarehouseToId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A destination warehouse is required");
        }
        Warehouse to = findWarehouse(req.getWarehouseToId());

        Warehouse from = null;
        if (req.getType() == StockMovementType.transfer) {
            if (req.getWarehouseFromId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A transfer needs a source warehouse");
            }
            if (req.getWarehouseFromId().equals(req.getWarehouseToId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Source and destination warehouses must differ");
            }
            from = findWarehouse(req.getWarehouseFromId());

            Inventory source = inventoryRepository.findByProductAndWarehouse(product, from)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "'" + product.getName() + "' has no stock in " + req.getWarehouseFromId()));
            int availableToMove = source.getOnHand() - source.getReserved();
            if (availableToMove < quantity) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Only " + availableToMove + " unit(s) of '" + product.getName()
                                + "' are free to move from " + from.getName());
            }
            source.setOnHand(source.getOnHand() - quantity);
            inventoryRepository.save(source);
        }

        Inventory destination = inventoryRepository.findByProductAndWarehouse(product, to)
                .orElseGet(() -> {
                    Inventory fresh = new Inventory();
                    fresh.setProduct(product);
                    fresh.setWarehouse(to);
                    fresh.setOnHand(0);
                    fresh.setReserved(0);
                    return fresh;
                });
        destination.setOnHand(destination.getOnHand() + quantity);
        inventoryRepository.save(destination);

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(req.getType());
        movement.setQuantity(quantity);
        movement.setWarehouseFrom(from);
        movement.setWarehouseTo(to);
        movement.setPurchasePrice(req.getPurchasePrice());
        movement.setSellingPrice(req.getSellingPrice());
        userRepository.findByUsernameIgnoreCase(actingUsername).ifPresent(movement::setCreatedBy);
        return toResponse(stockMovementRepository.save(movement));
    }

    // ------------------------------------------------------------------ helpers

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product " + id + " not found"));
    }

    private Warehouse findWarehouse(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Warehouse " + id + " not found"));
    }

    private InventoryResponse toResponse(Inventory i) {
        Product p = i.getProduct();
        return new InventoryResponse(
                p.getId(), p.getProductCode(), p.getName(),
                i.getWarehouse().getId(), i.getWarehouse().getName(),
                i.getOnHand(), i.getReserved(), i.getOnHand() - i.getReserved());
    }

    private StockMovementResponse toResponse(StockMovement m) {
        Product p = m.getProduct();
        User actor = m.getCreatedBy();
        return new StockMovementResponse(
                m.getId(), p.getId(), p.getProductCode(), p.getName(),
                m.getType(), m.getQuantity(),
                m.getWarehouseFrom() == null ? null : m.getWarehouseFrom().getId(),
                m.getWarehouseFrom() == null ? null : m.getWarehouseFrom().getName(),
                m.getWarehouseTo() == null ? null : m.getWarehouseTo().getId(),
                m.getWarehouseTo() == null ? null : m.getWarehouseTo().getName(),
                m.getPurchasePrice(), m.getSellingPrice(),
                actor == null ? null : actor.getFirstName() + " " + actor.getLastName(),
                m.getCreatedAt());
    }
}
