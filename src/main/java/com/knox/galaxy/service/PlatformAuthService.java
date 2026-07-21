package com.knox.galaxy.service;

import com.knox.galaxy.config.JwtTokenProvider;
import com.knox.galaxy.dto.PlatformLoginResponse;
import com.knox.galaxy.model.PlatformUser;
import com.knox.galaxy.repository.PlatformUserRepository;
import com.knox.galaxy.tenancy.TenantContext;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Login for KNOX staff. Runs entirely on the platform search_path — no tenant
 * is ever bound, and the token it mints carries no tenant_id.
 */
@Service
public class PlatformAuthService {

    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public PlatformAuthService(PlatformUserRepository platformUserRepository,
                               PasswordEncoder passwordEncoder,
                               JwtTokenProvider tokenProvider) {
        this.platformUserRepository = platformUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public PlatformLoginResponse login(String email, String rawPassword) {
        TenantContext.clear();

        PlatformUser user = platformUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        if (!user.isActive()) {
            throw new DisabledException("Account is disabled");
        }

        user.setLastLoginAt(LocalDateTime.now());
        platformUserRepository.save(user);

        String jwt = tokenProvider.generatePlatformToken(user.getEmail(), user.getId());
        return new PlatformLoginResponse(jwt, "Bearer", user.getEmail(), user.getFullName());
    }
}
