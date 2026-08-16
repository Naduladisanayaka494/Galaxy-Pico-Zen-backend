package com.knox.galaxy.controller;

import com.knox.galaxy.dto.NotificationFeedResponse;
import com.knox.galaxy.dto.NotificationResponse;
import com.knox.galaxy.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

/**
 * The alert bell feed.
 *
 * <p>A caller only ever sees their own notifications plus system-wide ones —
 * the recipient filter is applied in the query, not the response.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    /** Returns the feed and the unread count together — the badge needs both. */
    @GetMapping
    public ResponseEntity<NotificationFeedResponse> feed(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String username = userDetails == null ? null : userDetails.getUsername();
        return ResponseEntity.ok(notificationService.feed(username, unreadOnly, page, size));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails == null ? null : userDetails.getUsername();
        return ResponseEntity.ok(notificationService.markRead(id, username));
    }

    /** Clears the badge in one call. Returns how many rows changed. */
    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Long>> markAllRead(
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails == null ? null : userDetails.getUsername();
        return ResponseEntity.ok(Collections.singletonMap(
                "updated", notificationService.markAllRead(username)));
    }
}
