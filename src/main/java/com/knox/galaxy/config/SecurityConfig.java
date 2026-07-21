package com.knox.galaxy.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    // Comma-separated. No wildcard: this is combined with allowCredentials(true)
    // for the auth cookies, and CORS forbids wildcard-origin + credentials
    // together in any config that isn't actively meaningless.
    @Value("#{'${galaxy.cors.allowed-origins:http://localhost:5173}'.split(',')}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors().and()
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
            .authorizeRequests()
            // Only login is open. /register used to sit behind /api/auth/** and
            // was therefore unauthenticated — with tenants that would let anyone
            // create an 'owner' in any tenant, so it is now authenticated and
            // role-gated in AuthController.
            .antMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
            .antMatchers("/api/platform/auth/login").permitAll()
            // Everything else under /api/platform is KNOX staff only. Enforced
            // here rather than per-controller so a new platform route cannot
            // accidentally ship unguarded. A tenant-scoped token never carries
            // ROLE_PLATFORM_ADMIN, so it cannot reach these.
            .antMatchers("/api/platform/**").hasRole("PLATFORM_ADMIN")
            .anyRequest().authenticated();

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // Explicit allow-list, not "*": wildcard-origin + allowCredentials(true)
        // together means any site can make credentialed requests, which defeats
        // the point of the auth cookies being SameSite-restricted in the first
        // place. Configure real origins via galaxy.cors.allowed-origins in prod.
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedHeaders(Arrays.asList("Origin", "Content-Type", "Accept", "Authorization", "X-XSRF-Token"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
