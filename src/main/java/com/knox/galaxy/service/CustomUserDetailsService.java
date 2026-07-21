package com.knox.galaxy.service;

import com.knox.galaxy.model.User;
import com.knox.galaxy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

/**
 * Loads the tenant-local user. Every method here reads {@code users} in
 * whichever schema the current {@link com.knox.galaxy.tenancy.TenantContext}
 * points at, so a tenant must be bound before calling.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Usernames are only unique within a tenant, so this is safe only under a
     * bound tenant context. Retained for the Spring Security
     * DaoAuthenticationProvider contract.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        return toUserDetails(user);
    }

    /**
     * Preferred path for token-authenticated requests: the token carries the
     * tenant-local user id, which is stable and unambiguous.
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserByLocalId(Long localUserId) {
        User user = userRepository.findById(localUserId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + localUserId));
        return toUserDetails(user);
    }

    private UserDetails toUserDetails(User user) {
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash() == null ? "" : user.getPasswordHash(),
                user.isActive(),
                true, true, true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()))
        );
    }
}
