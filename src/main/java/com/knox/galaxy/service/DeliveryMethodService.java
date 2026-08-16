package com.knox.galaxy.service;

import com.knox.galaxy.dto.DeliveryMethodRequest;
import com.knox.galaxy.dto.DeliveryMethodResponse;
import com.knox.galaxy.model.DeliveryMethod;
import com.knox.galaxy.repository.DeliveryMethodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Delivery methods and their default charge, offered on the order form.
 *
 * <p>Rows are deactivated rather than deleted when already referenced by an
 * order — {@code orders.delivery_method_id} keeps historical orders readable.
 */
@Service
public class DeliveryMethodService {

    @Autowired
    private DeliveryMethodRepository deliveryMethodRepository;

    @Transactional(readOnly = true)
    public List<DeliveryMethodResponse> list(boolean activeOnly) {
        List<DeliveryMethod> methods = activeOnly
                ? deliveryMethodRepository.findAllByIsActiveOrderByNameAsc(true)
                : deliveryMethodRepository.findAllByOrderByNameAsc();
        return methods.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public DeliveryMethodResponse create(DeliveryMethodRequest req) {
        requireNameAvailable(req.getName(), null);
        DeliveryMethod method = new DeliveryMethod();
        apply(method, req);
        return toResponse(deliveryMethodRepository.save(method));
    }

    @Transactional
    public DeliveryMethodResponse update(Long id, DeliveryMethodRequest req) {
        DeliveryMethod method = findOrThrow(id);
        requireNameAvailable(req.getName(), id);
        apply(method, req);
        return toResponse(deliveryMethodRepository.save(method));
    }

    @Transactional
    public void delete(Long id) {
        deliveryMethodRepository.delete(findOrThrow(id));
    }

    private void apply(DeliveryMethod method, DeliveryMethodRequest req) {
        method.setName(req.getName().trim());
        method.setCharge(req.getCharge());
        method.setActive(req.isActive());
    }

    private void requireNameAvailable(String name, Long selfId) {
        deliveryMethodRepository.findByNameIgnoreCase(name.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A delivery method named '" + name.trim() + "' already exists");
            }
        });
    }

    private DeliveryMethod findOrThrow(Long id) {
        return deliveryMethodRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Delivery method " + id + " not found"));
    }

    private DeliveryMethodResponse toResponse(DeliveryMethod m) {
        return new DeliveryMethodResponse(m.getId(), m.getName(), m.getCharge(), m.isActive());
    }
}
