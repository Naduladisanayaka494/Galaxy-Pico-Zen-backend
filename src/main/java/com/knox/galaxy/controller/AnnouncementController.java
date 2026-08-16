package com.knox.galaxy.controller;

import com.knox.galaxy.model.Announcement;
import com.knox.galaxy.repository.AnnouncementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dashboard announcement slides (§4).
 *
 * <p>Read-only for tenants: slots are filled by KNOX. Thin enough to go
 * straight to the repository — the entity is already the wire shape.
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    @Autowired
    private AnnouncementRepository announcementRepository;

    /** Active slides in slot order; pass activeOnly=false to see empty slots. */
    @GetMapping
    public ResponseEntity<List<Announcement>> list(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(activeOnly
                ? announcementRepository.findAllByIsActiveOrderByPositionAsc(true)
                : announcementRepository.findAllByOrderByPositionAsc());
    }
}
