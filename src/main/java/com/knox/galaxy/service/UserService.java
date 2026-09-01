package com.knox.galaxy.service;

import com.knox.galaxy.dto.ChangePasswordRequest;
import com.knox.galaxy.dto.UpdateProfileRequest;
import com.knox.galaxy.model.TenantUser;
import com.knox.galaxy.model.User;
import com.knox.galaxy.repository.TenantUserRepository;
import com.knox.galaxy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantUserRepository tenantUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username);
    }

    /**
     * Creates the tenant-local profile row. Credentials are the platform's job
     * ({@code knox.tenant_users}); the hash stored here is legacy and unused by
     * login. Callers should go through {@code AuthService.register}.
     */
    @Transactional
    public User registerUser(User user) {
        if (userRepository.existsByUsernameIgnoreCase(user.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (user.getEmail() != null && userRepository.existsByEmailIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    @Transactional
    public User updateProfile(String username, UpdateProfileRequest request) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    /**
     * The signed-in person changing their own password from the Profile screen.
     *
     * <p>The authoritative credential is {@code knox.tenant_users.password_hash}
     * (what login checks); the tenant-local {@code users.password_hash} is kept
     * in step behind it. Safe as one transaction — the tenant schema is already
     * bound for the whole request and {@link TenantUser} is schema-qualified to
     * {@code knox}, so no {@code TenantContext} switch is needed (same reasoning
     * as {@code TenantUserAdminService}).
     *
     * <p>Deliberately does not touch refresh tokens: this is a voluntary change
     * by someone already holding a valid session, not a recovery from
     * compromise, so other devices staying logged in is the expected behaviour.
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No login credential is linked to this account");
        }

        TenantUser directory = tenantUserRepository.findByEmailIgnoreCase(user.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No login credential is linked to this account"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), directory.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), directory.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "New password must be different from the current one");
        }

        String encoded = passwordEncoder.encode(request.getNewPassword());
        LocalDateTime now = LocalDateTime.now();

        directory.setPasswordHash(encoded);
        directory.setUpdatedAt(now);
        tenantUserRepository.save(directory);

        user.setPasswordHash(encoded);
        user.setUpdatedAt(now);
        userRepository.save(user);
    }
}

