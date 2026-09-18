package com.knox.galaxy.service;

import com.knox.galaxy.dto.DeliveryMethodRequest;
import com.knox.galaxy.dto.DeliveryMethodResponse;
import com.knox.galaxy.model.DeliveryChargeKind;
import com.knox.galaxy.model.DeliveryMethod;
import com.knox.galaxy.model.DeliveryMethodRate;
import com.knox.galaxy.model.DeliveryRegionType;
import com.knox.galaxy.repository.DeliveryMethodRateRepository;
import com.knox.galaxy.repository.DeliveryMethodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Delivery methods, their default charge, and their per-region rates.
 *
 * <p>A method prices an order in one of four ways, from the two independent
 * axes the settings popup exposes: a flat LKR amount or a percent of the items
 * subtotal, applied either everywhere or per province / per district. The
 * region rates are edited as one grid with the method itself, so a save
 * replaces the whole set rather than diffing row by row — see {@link #applyRates}.
 *
 * <p>Rows are deactivated rather than deleted when already referenced by an
 * order — {@code orders.delivery_method_id} keeps historical orders readable.
 */
@Service
public class DeliveryMethodService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Autowired
    private DeliveryMethodRepository deliveryMethodRepository;

    @Autowired
    private DeliveryMethodRateRepository rateRepository;

    @Transactional(readOnly = true)
    public List<DeliveryMethodResponse> list(boolean activeOnly) {
        List<DeliveryMethod> methods = activeOnly
                ? deliveryMethodRepository.findAllByIsActiveOrderByNameAsc(true)
                : deliveryMethodRepository.findAllByOrderByNameAsc();
        if (methods.isEmpty()) {
            return Collections.emptyList();
        }

        // One rate query for the whole page rather than one per method.
        Map<Long, List<DeliveryMethodRate>> ratesByMethod = rateRepository
                .findByDeliveryMethodIdIn(methods.stream()
                        .map(DeliveryMethod::getId).collect(Collectors.toList()))
                .stream()
                .collect(Collectors.groupingBy(r -> r.getDeliveryMethod().getId()));

        return methods.stream()
                .map(m -> toResponse(m, ratesByMethod.getOrDefault(m.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    @Transactional
    public DeliveryMethodResponse create(DeliveryMethodRequest req) {
        requireNameAvailable(req.getName(), null);
        DeliveryMethod method = new DeliveryMethod();
        apply(method, req);
        DeliveryMethod saved = deliveryMethodRepository.save(method);
        return toResponse(saved, applyRates(saved, req));
    }

    @Transactional
    public DeliveryMethodResponse update(Long id, DeliveryMethodRequest req) {
        DeliveryMethod method = findOrThrow(id);
        requireNameAvailable(req.getName(), id);
        apply(method, req);
        DeliveryMethod saved = deliveryMethodRepository.save(method);
        return toResponse(saved, applyRates(saved, req));
    }

    @Transactional
    public void delete(Long id) {
        DeliveryMethod method = findOrThrow(id);
        // The FK is ON DELETE CASCADE, but JPA does not know that; clearing the
        // children here keeps the persistence context honest either way.
        rateRepository.deleteByDeliveryMethodId(id);
        rateRepository.flush();
        deliveryMethodRepository.delete(method);
    }

    private void apply(DeliveryMethod method, DeliveryMethodRequest req) {
        DeliveryChargeKind kind = req.getChargeKind() == null
                ? DeliveryChargeKind.fixed
                : req.getChargeKind();
        DeliveryRegionType scope = req.getRateScope() == null
                ? DeliveryRegionType.flat
                : req.getRateScope();

        requireWithinPercentRange(kind, req.getCharge(), "Charge");

        method.setName(req.getName().trim());
        method.setCharge(req.getCharge());
        method.setChargeKind(kind);
        method.setRateScope(scope);
        method.setActive(req.isActive());
    }

    /**
     * Replaces the method's whole rate set with what the popup submitted.
     *
     * <p>Blank rows are dropped rather than stored as zero: an empty box in the
     * grid means "no override, use the method's own charge", which is not the
     * same as a region that genuinely delivers free.
     */
    private List<DeliveryMethodRate> applyRates(DeliveryMethod method, DeliveryMethodRequest req) {
        rateRepository.deleteByDeliveryMethodId(method.getId());
        // Flush before re-inserting: the old and new rows collide on
        // UNIQUE (delivery_method_id, region_type, region_name) if JPA is left
        // to decide the statement order at commit time.
        rateRepository.flush();

        if (req.getRates() == null || req.getRates().isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> seen = new HashSet<>();
        List<DeliveryMethodRate> rows = new ArrayList<>();
        for (DeliveryMethodRequest.DeliveryMethodRateRequest r : req.getRates()) {
            if (r.getRegionType() == DeliveryRegionType.flat) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "'flat' is a scope, not a region a rate can name");
            }
            String region = r.getRegionName().trim();
            if (region.isEmpty()) {
                continue;
            }
            if (!seen.add(r.getRegionType() + "|" + region.toLowerCase())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Duplicate rate for " + r.getRegionType() + " '" + region + "'");
            }
            requireWithinPercentRange(method.getChargeKind(), r.getCharge(), region);

            DeliveryMethodRate rate = new DeliveryMethodRate();
            rate.setDeliveryMethod(method);
            rate.setRegionType(r.getRegionType());
            rate.setRegionName(region);
            rate.setCharge(r.getCharge());
            rows.add(rate);
        }
        return rateRepository.saveAll(rows);
    }

    /** A percentage charge is 0-100, the same rule discount codes follow. */
    private void requireWithinPercentRange(DeliveryChargeKind kind, BigDecimal value, String label) {
        if (kind == DeliveryChargeKind.percentage && value != null && value.compareTo(HUNDRED) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + " cannot be more than 100%");
        }
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

    private DeliveryMethodResponse toResponse(DeliveryMethod m, List<DeliveryMethodRate> rates) {
        List<DeliveryMethodResponse.DeliveryMethodRateResponse> rateDtos = rates.stream()
                .map(r -> new DeliveryMethodResponse.DeliveryMethodRateResponse(
                        r.getId(), r.getRegionType(), r.getRegionName(), r.getCharge()))
                .collect(Collectors.toList());
        return new DeliveryMethodResponse(m.getId(), m.getName(), m.getCharge(),
                m.getChargeKind(), m.getRateScope(), rateDtos, m.isActive());
    }
}
