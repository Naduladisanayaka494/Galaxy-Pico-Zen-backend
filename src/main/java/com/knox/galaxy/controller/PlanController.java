package com.knox.galaxy.controller;

import com.knox.galaxy.model.KnoxPlanCatalogue;
import com.knox.galaxy.service.ClientService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * KNOX's pricing catalogue (§16.5) — setup fees, subscription fees, per-order
 * rates. Its own route rather than /clients/plans, which would collide with
 * /clients/{id}.
 */
@RestController
@RequestMapping("/api/platform/plans")
public class PlanController {

    private final ClientService clientService;

    public PlanController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    public List<KnoxPlanCatalogue> plans() {
        return clientService.plans();
    }
}
