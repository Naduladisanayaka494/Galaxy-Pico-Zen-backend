package com.knox.galaxy.service;

import com.knox.galaxy.config.JwtTokenProvider;
import com.knox.galaxy.dto.LoginResponse;
import com.knox.galaxy.dto.RegisterRequest;
import com.knox.galaxy.model.*;
import com.knox.galaxy.repository.RoleRepository;
import com.knox.galaxy.repository.TenantRepository;
import com.knox.galaxy.repository.TenantUserRepository;
import com.knox.galaxy.repository.UserRepository;
import com.knox.galaxy.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * Login and registration across the platform/tenant boundary.
 *
 * <p>Nothing here is annotated {@code @Transactional}. That is deliberate: the
 * tenant identifier is bound when a Hibernate Session opens, so the knox lookup
 * and the tenant-schema lookup must land in separate sessions. One transaction
 * spanning both would pin every query to whichever schema was current when it
 * started, and the tenant read would silently hit the wrong schema.
 */
@Service
public class AuthService {

    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final RoleRepository roleRepository;

    public AuthService(TenantUserRepository tenantUserRepository,
                       TenantRepository tenantRepository,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       RefreshTokenService refreshTokenService,
                       RoleRepository roleRepository) {
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.roleRepository = roleRepository;
    }

    /** Bundles the JSON body with the refresh token, which never appears in JSON — only as an httpOnly cookie. */
    public static class LoginResult {
        public final LoginResponse body;
        public final RefreshTokenService.IssuedToken refreshToken;

        LoginResult(LoginResponse body, RefreshTokenService.IssuedToken refreshToken) {
            this.body = body;
            this.refreshToken = refreshToken;
        }
    }

    public LoginResult login(String email, String rawPassword) {
        TenantContext.clear();

        TenantUser tenantUser = tenantUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(rawPassword, tenantUser.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        if (tenantUser.getStatus() != TenantUserStatus.active) {
            throw new DisabledException("Account is disabled");
        }

        Tenant tenant = tenantRepository.findById(tenantUser.getTenantId())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (tenant.getStatus() != TenantStatus.active) {
            throw new DisabledException("Tenant is " + tenant.getStatus());
        }

        TenantContext.setSchema(tenant.getSchemaName());
        try {
            User localUser = userRepository.findById(tenantUser.getLocalUserId())
                    .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
            if (!localUser.isActive()) {
                throw new DisabledException("Account is disabled");
            }

            String role = localUser.getRole().getName();
            String jwt = tokenProvider.generateToken(
                    tenantUser.getEmail(), tenant.getId(), localUser.getId(), role);
            RefreshTokenService.IssuedToken refreshToken = refreshTokenService.issue(tenantUser.getId());

            return new LoginResult(new LoginResponse(jwt, "Bearer", localUser.getUsername(), role), refreshToken);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Rotates a refresh token and mints a fresh access token for whichever
     * tenant/user it belongs to. Separate from login(): no password is
     * involved, and the tenant is derived from the token, not the caller.
     */
    public LoginResult refresh(String rawRefreshToken) {
        RefreshTokenService.IssuedToken rotated = refreshTokenService.rotate(rawRefreshToken);
        TenantContext.clear();

        TenantUser tenantUser = tenantUserRepository.findById(rotated.tenantUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (tenantUser.getStatus() != TenantUserStatus.active) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is disabled");
        }

        Tenant tenant = tenantRepository.findById(tenantUser.getTenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (tenant.getStatus() != TenantStatus.active) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tenant is " + tenant.getStatus());
        }

        TenantContext.setSchema(tenant.getSchemaName());
        try {
            User localUser = userRepository.findById(tenantUser.getLocalUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
            if (!localUser.isActive()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is disabled");
            }
            String role = localUser.getRole().getName();
            String jwt = tokenProvider.generateToken(tenantUser.getEmail(), tenant.getId(), localUser.getId(), role);
            return new LoginResult(new LoginResponse(jwt, "Bearer", localUser.getUsername(), role), rotated);
        } finally {
            TenantContext.clear();
        }
    }

    public User register(Long tenantId, RegisterRequest command) {
        TenantContext.clear();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + tenantId));

        if (command.getEmail() == null || command.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required: it is the login identifier");
        }
        if (tenantUserRepository.existsByEmailIgnoreCase(command.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        TenantContext.setSchema(tenant.getSchemaName());
        User saved;
        try {
            User user = new User();
            user.setUsername(command.getUsername());
            user.setPasswordHash(command.getPassword());
            user.setFirstName(command.getFirstName());
            user.setLastName(command.getLastName());
            user.setEmail(command.getEmail());
            user.setRole(roleRepository.findByName(command.getRole())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + command.getRole())));
            user.setPhone(command.getPhone());
            user.setAvatarUrl(command.getAvatarUrl());

            if (userRepository.existsByUsernameIgnoreCase(user.getUsername())) {
                throw new IllegalArgumentException("Username already exists");
            }
            user.setPasswordHash(passwordEncoder.encode(command.getPassword()));
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            saved = userRepository.save(user);
        } finally {
            TenantContext.clear();
        }

        try {
            TenantUser tenantUser = new TenantUser();
            tenantUser.setTenantId(tenantId);
            tenantUser.setEmail(command.getEmail());
            tenantUser.setPasswordHash(passwordEncoder.encode(command.getPassword()));
            tenantUser.setLocalUserId(saved.getId());
            tenantUser.setStatus(TenantUserStatus.active);
            tenantUserRepository.save(tenantUser);
        } catch (RuntimeException e) {
          
            TenantContext.setSchema(tenant.getSchemaName());
            try {
                userRepository.deleteById(saved.getId());
            } finally {
                TenantContext.clear();
            }
            throw e;
        }

        return saved;
    }
}
