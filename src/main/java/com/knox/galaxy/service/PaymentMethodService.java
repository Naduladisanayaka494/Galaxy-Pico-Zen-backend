package com.knox.galaxy.service;

import com.knox.galaxy.dto.PaymentMethodRequest;
import com.knox.galaxy.dto.PaymentMethodResponse;
import com.knox.galaxy.model.PaymentMethod;
import com.knox.galaxy.repository.PaymentMethodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/** Payment method tags offered on the order form. */
@Service
public class PaymentMethodService {

    @Autowired
    private PaymentMethodRepository paymentMethodRepository;

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> list(boolean activeOnly) {
        List<PaymentMethod> methods = activeOnly
                ? paymentMethodRepository.findAllByIsActiveOrderByNameAsc(true)
                : paymentMethodRepository.findAllByOrderByNameAsc();
        return methods.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public PaymentMethodResponse create(PaymentMethodRequest req) {
        requireNameAvailable(req.getName(), null);
        PaymentMethod method = new PaymentMethod();
        apply(method, req);
        return toResponse(paymentMethodRepository.save(method));
    }

    @Transactional
    public PaymentMethodResponse update(Long id, PaymentMethodRequest req) {
        PaymentMethod method = findOrThrow(id);
        requireNameAvailable(req.getName(), id);
        apply(method, req);
        return toResponse(paymentMethodRepository.save(method));
    }

    @Transactional
    public void delete(Long id) {
        paymentMethodRepository.delete(findOrThrow(id));
    }

    private void apply(PaymentMethod method, PaymentMethodRequest req) {
        method.setName(req.getName().trim());
        method.setActive(req.isActive());
    }

    private void requireNameAvailable(String name, Long selfId) {
        paymentMethodRepository.findByNameIgnoreCase(name.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A payment method named '" + name.trim() + "' already exists");
            }
        });
    }

    private PaymentMethod findOrThrow(Long id) {
        return paymentMethodRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Payment method " + id + " not found"));
    }

    private PaymentMethodResponse toResponse(PaymentMethod m) {
        return new PaymentMethodResponse(m.getId(), m.getName(), m.isActive());
    }
}
