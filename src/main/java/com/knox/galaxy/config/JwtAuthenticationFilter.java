package com.knox.galaxy.config;

import com.knox.galaxy.model.PlatformUser;
import com.knox.galaxy.repository.PlatformUserRepository;
import com.knox.galaxy.service.CustomUserDetailsService;
import com.knox.galaxy.tenancy.TenantContext;
import com.knox.galaxy.tenancy.TenantResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/**
 * Binds the request to a tenant schema from the token's tenant claim, then
 * authenticates against that tenant's own users table.
 *
 * <p>The context is bound before any repository call and released in a finally
 * block. Tomcat reuses threads, so a context left behind here would be
 * inherited by the next request on this thread — possibly another tenant's.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private TenantResolver tenantResolver;

    @Autowired
    private PlatformUserRepository platformUserRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                authenticate(request, jwt);
            }

            filterChain.doFilter(request, response);
        } finally {
            // Must outlive doFilter (controllers need the context) but must not
            // outlive the request.
            TenantContext.clear();
        }
    }

    private void authenticate(HttpServletRequest request, String jwt) {
        try {
            if (tokenProvider.isPlatformToken(jwt)) {
                authenticatePlatformUser(request, jwt);
                return;
            }

            Long tenantId = tokenProvider.getTenantIdFromJWT(jwt);
            Long localUserId = tokenProvider.getUserIdFromJWT(jwt);

            // Unknown tenant throws; the request then proceeds unauthenticated
            // and is rejected by authorization. It must never fall back to a
            // default schema.
            TenantContext.bind(tenantId, tenantResolver.schemaFor(tenantId));

            UserDetails userDetails = userDetailsService.loadUserByLocalId(localUserId);
            if (!userDetails.isEnabled()) {
                throw new org.springframework.security.authentication.DisabledException("Account is disabled");
            }
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception ex) {
            // Leave the request unauthenticated, and make sure no half-bound
            // tenant context survives into the controller.
            TenantContext.clear();
            SecurityContextHolder.clearContext();
            log.debug("Rejecting token: {}", ex.getMessage());
        }
    }

    /**
     * KNOX staff. Deliberately binds no TenantContext: a platform token must
     * never put a tenant schema on the search_path, so if one of these ever
     * reaches a tenant-scoped repository the query fails loudly against
     * `public` rather than silently reading some tenant's rows.
     */
    private void authenticatePlatformUser(HttpServletRequest request, String jwt) {
        Long platformUserId = tokenProvider.getUserIdFromJWT(jwt);
        PlatformUser user = platformUserRepository.findById(platformUserId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown platform user: " + platformUserId));
        if (!user.isActive()) {
            throw new IllegalArgumentException("Platform user disabled: " + platformUserId);
        }

        UserDetails principal = new org.springframework.security.core.userdetails.User(
                user.getEmail(), "", true, true, true, true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
