package com.knox.galaxy.controller;

import com.knox.galaxy.dto.UpdateProfileRequest;
import com.knox.galaxy.dto.UserResponseDto;
import com.knox.galaxy.model.User;
import com.knox.galaxy.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        User user = userService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userDetails.getUsername()));
        return ResponseEntity.ok(toDto(user));
    }

    /** Name and phone only — see UpdateProfileRequest for why email isn't editable here. */
    @PutMapping("/me")
    public ResponseEntity<?> updateCurrentUser(@AuthenticationPrincipal UserDetails userDetails,
                                               @Valid @RequestBody UpdateProfileRequest request) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        User user = userService.updateProfile(userDetails.getUsername(), request);
        return ResponseEntity.ok(toDto(user));
    }

    private UserResponseDto toDto(User user) {
        UserResponseDto dto = new UserResponseDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setPhone(user.getPhone());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setCommissionEnabled(user.isCommissionEnabled());
        dto.setCommissionMethod(user.getCommissionMethod());
        dto.setCommissionPercent(user.getCommissionPercent());
        dto.setCommissionUnitAmount(user.getCommissionUnitAmount());
        dto.setCommissionMinUnits(user.getCommissionMinUnits());
        return dto;
    }
}
