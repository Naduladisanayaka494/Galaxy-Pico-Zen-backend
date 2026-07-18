package com.knox.galaxy.controller;

import com.knox.galaxy.dto.ClientRequest;
import com.knox.galaxy.dto.ClientResponse;
import com.knox.galaxy.service.ClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * KNOX Client Manager (§16). Authorized by SecurityConfig's
 * {@code /api/platform/** -> PLATFORM_ADMIN} rule: KNOX staff only, never a
 * tenant token.
 */
@RestController
@RequestMapping("/api/platform/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    public List<ClientResponse> list() {
        return clientService.list();
    }

    @GetMapping("/{id}")
    public ClientResponse get(@PathVariable Long id) {
        return clientService.get(id);
    }

    @PostMapping
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.create(request));
    }

    @PutMapping("/{id}")
    public ClientResponse update(@PathVariable Long id, @Valid @RequestBody ClientRequest request) {
        return clientService.update(id, request);
    }

    @PutMapping("/{id}/blocked")
    public ClientResponse setBlocked(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean blocked = body.get("blocked");
        if (blocked == null) {
            throw new IllegalArgumentException("Body must contain 'blocked'");
        }
        return clientService.setBlocked(id, blocked);
    }

    @PutMapping("/{id}/setup-installments/{installmentNo}")
    public ClientResponse setInstallmentPaid(@PathVariable Long id,
                                             @PathVariable short installmentNo,
                                             @RequestBody Map<String, Boolean> body) {
        Boolean paid = body.get("paid");
        if (paid == null) {
            throw new IllegalArgumentException("Body must contain 'paid'");
        }
        return clientService.setSetupInstallmentPaid(id, installmentNo, paid);
    }

    /**
     * Sets the paid flag and/or the amount for one billing period.
     * Both fields are optional: per-order clients save an amount first, then
     * mark it paid as a separate action.
     */
    @PutMapping("/{id}/periods/{periodKey}")
    public ClientResponse setPeriod(@PathVariable Long id,
                                    @PathVariable String periodKey,
                                    @RequestBody PeriodPaymentRequest body) {
        return clientService.setPeriodPayment(id, periodKey, body.getPaid(), body.getAmount());
    }

    public static class PeriodPaymentRequest {
        private Boolean paid;
        private BigDecimal amount;

        public Boolean getPaid() {
            return paid;
        }

        public void setPaid(Boolean paid) {
            this.paid = paid;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }
}
