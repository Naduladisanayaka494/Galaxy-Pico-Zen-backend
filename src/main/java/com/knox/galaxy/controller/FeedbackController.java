package com.knox.galaxy.controller;

import com.knox.galaxy.dto.FeedbackRequest;
import com.knox.galaxy.dto.FeedbackResponse;
import com.knox.galaxy.service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/** The Help page's feedback form (§14.3). */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<FeedbackResponse> submit(
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String username = userDetails == null ? null : userDetails.getUsername();
        return ResponseEntity.status(HttpStatus.CREATED).body(feedbackService.submit(request, username));
    }

    /** What this tenant has already sent, newest first. */
    @GetMapping
    public ResponseEntity<List<FeedbackResponse>> list() {
        return ResponseEntity.ok(feedbackService.list());
    }
}
