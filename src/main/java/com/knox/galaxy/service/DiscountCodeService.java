package com.knox.galaxy.service;

import com.knox.galaxy.dto.DiscountCodeRequest;
import com.knox.galaxy.dto.DiscountCodeResponse;
import com.knox.galaxy.model.DiscountCode;
import com.knox.galaxy.model.DiscountType;
import com.knox.galaxy.repository.DiscountCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/** Discount codes applied at order time. Codes are unique and stored upper-case. */
@Service
public class DiscountCodeService {

    private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("100");

    @Autowired
    private DiscountCodeRepository discountCodeRepository;

    @Transactional(readOnly = true)
    public List<DiscountCodeResponse> list(boolean activeOnly) {
        List<DiscountCode> codes = activeOnly
                ? discountCodeRepository.findAllByIsActiveOrderByCodeAsc(true)
                : discountCodeRepository.findAllByOrderByCodeAsc();
        return codes.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public DiscountCodeResponse create(DiscountCodeRequest req) {
        requireCodeAvailable(req.getCode(), null);
        DiscountCode code = new DiscountCode();
        apply(code, req);
        return toResponse(discountCodeRepository.save(code));
    }

    @Transactional
    public DiscountCodeResponse update(Long id, DiscountCodeRequest req) {
        DiscountCode code = findOrThrow(id);
        requireCodeAvailable(req.getCode(), id);
        apply(code, req);
        return toResponse(discountCodeRepository.save(code));
    }

    @Transactional
    public void delete(Long id) {
        discountCodeRepository.delete(findOrThrow(id));
    }

    private void apply(DiscountCode code, DiscountCodeRequest req) {
        // The schema has CHECK (kind <> 'percentage' OR value <= 100); check it
        // here first so the UI gets a readable 400 rather than a constraint 500.
        if (req.getKind() == DiscountType.percentage && req.getValue().compareTo(MAX_PERCENTAGE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A percentage discount cannot exceed 100%");
        }
        code.setCode(req.getCode().trim().toUpperCase());
        code.setKind(req.getKind());
        code.setValue(req.getValue());
        code.setActive(req.isActive());
    }

    private void requireCodeAvailable(String code, Long selfId) {
        discountCodeRepository.findByCodeIgnoreCase(code.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Discount code '" + code.trim().toUpperCase() + "' already exists");
            }
        });
    }

    private DiscountCode findOrThrow(Long id) {
        return discountCodeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Discount code " + id + " not found"));
    }

    private DiscountCodeResponse toResponse(DiscountCode d) {
        return new DiscountCodeResponse(d.getId(), d.getCode(), d.getKind(), d.getValue(), d.isActive());
    }
}
