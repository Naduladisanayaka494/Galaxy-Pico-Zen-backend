package com.knox.galaxy.service;

import com.knox.galaxy.dto.WarehouseRequest;
import com.knox.galaxy.dto.WarehouseResponse;
import com.knox.galaxy.model.Warehouse;
import com.knox.galaxy.repository.InventoryRepository;
import com.knox.galaxy.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/** Warehouse CRUD plus the live stock totals the Warehouses page renders. */
@Service
public class WarehouseService {

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public List<WarehouseResponse> list(boolean activeOnly) {
        List<Warehouse> warehouses = activeOnly
                ? warehouseRepository.findAllByIsActiveOrderByNameAsc(true)
                : warehouseRepository.findAllByOrderByNameAsc();
        return warehouses.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WarehouseResponse get(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest req) {
        requireCodeAvailable(req.getCode(), null);
        Warehouse warehouse = new Warehouse();
        apply(warehouse, req);
        return toResponse(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseRequest req) {
        Warehouse warehouse = findOrThrow(id);
        requireCodeAvailable(req.getCode(), id);
        apply(warehouse, req);
        return toResponse(warehouseRepository.save(warehouse));
    }

    /**
     * Inventory rows cascade on delete, so removing a warehouse that still
     * holds stock would silently destroy those counts. Blocked — deactivating
     * is the reversible way to take a warehouse out of circulation.
     */
    @Transactional
    public void delete(Long id) {
        Warehouse warehouse = findOrThrow(id);
        long stored = inventoryRepository.countByWarehouse(warehouse);
        if (stored > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "'" + warehouse.getName() + "' still holds stock for " + stored
                            + " product(s). Move the stock out, or deactivate it instead.");
        }
        warehouseRepository.delete(warehouse);
    }

    private void apply(Warehouse warehouse, WarehouseRequest req) {
        warehouse.setName(req.getName().trim());
        warehouse.setCode(req.getCode().trim().toUpperCase());
        warehouse.setLocation(req.getLocation());
        warehouse.setType(req.getType());
        warehouse.setManager(req.getManager());
        warehouse.setCapacity(req.getCapacity());
        warehouse.setActive(req.isActive());
    }

    private void requireCodeAvailable(String code, Long selfId) {
        warehouseRepository.findByCodeIgnoreCase(code.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Warehouse code '" + code.trim().toUpperCase() + "' is already in use");
            }
        });
    }

    private Warehouse findOrThrow(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Warehouse " + id + " not found"));
    }

    WarehouseResponse toResponse(Warehouse w) {
        int onHand = inventoryRepository.sumOnHandByWarehouseId(w.getId());
        Integer capacity = w.getCapacity();
        Integer fill = (capacity == null || capacity == 0)
                ? null
                : (int) Math.round(onHand * 100.0 / capacity);
        return new WarehouseResponse(
                w.getId(), w.getName(), w.getCode(), w.getLocation(),
                w.getType(), w.getManager(), capacity, w.isActive(),
                onHand, inventoryRepository.countByWarehouse(w), fill);
    }
}
