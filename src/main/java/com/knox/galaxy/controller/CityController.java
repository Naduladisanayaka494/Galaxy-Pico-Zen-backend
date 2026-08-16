package com.knox.galaxy.controller;

import com.knox.galaxy.dto.CityRequest;
import com.knox.galaxy.dto.CityResponse;
import com.knox.galaxy.service.CityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/** Province → District → Town list behind the delivery-address dropdowns. */
@RestController
@RequestMapping("/api/cities")
public class CityController {

    @Autowired
    private CityService cityService;

    /**
     * @param activeOnly defaults to true (order form); the settings screen
     *                   passes false to manage deactivated rows.
     */
    @GetMapping
    public ResponseEntity<List<CityResponse>> list(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(cityService.list(activeOnly));
    }

    @PostMapping
    public ResponseEntity<CityResponse> create(@Valid @RequestBody CityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cityService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CityResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody CityRequest request) {
        return ResponseEntity.ok(cityService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        cityService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
