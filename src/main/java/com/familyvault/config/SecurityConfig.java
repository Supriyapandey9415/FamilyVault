package com.familyvault.config;

import com.familyvault.auth.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final List<String> allowedOrigins;
    private final boolean secureCookie;
    private final String sameSite;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthFilter,
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174}")
            List<String> allowedOrigins,
            @Value("${app.auth-cookie.secure:false}") boolean secureCookie,
            @Value("${app.auth-cookie.same-site:Lax}") String sameSite) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
        this.allowedOrigins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokens = new CookieCsrfTokenRepository();
        csrfTokens.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(secureCookie).sameSite(sameSite).path("/"));
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .requireCsrfProtectionMatcher(request -> {
                    boolean safe = List.of("GET", "HEAD", "OPTIONS", "TRACE").contains(request.getMethod());
                    String authorization = request.getHeader("Authorization");
                    // Explicit bearer requests never fall back to cookie authentication.
                    return !safe && (authorization == null || !authorization.startsWith("Bearer "));
                }))
            .headers(headers -> headers.referrerPolicy(policy -> policy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"Authentication is required\",\"data\":null}");
            }).accessDeniedHandler((request, response, exception) -> {
                response.setStatus(403);
                response.setContentType("application/json");
                // Only a CsrfFilter rejection is safe for clients to refresh and replay.
                if (exception instanceof CsrfException) {
                    response.getWriter().write("{\"success\":false,\"code\":\"CSRF_TOKEN_INVALID\",\"message\":\"Security token is missing or expired\",\"data\":null}");
                } else {
                    response.getWriter().write("{\"success\":false,\"code\":\"ACCESS_DENIED\",\"message\":\"Access denied\",\"data\":null}");
                }
            }))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers("/uploads/**").denyAll()
                .requestMatchers(HttpMethod.GET,
                    "/api/health", "/api/auth/config", "/auth/config",
                    "/api/auth/csrf", "/auth/csrf",
                    "/api/auth/apple/config", "/auth/apple/config",
                    "/api/auth/verify-reset-token", "/auth/verify-reset-token",
                    "/api/invitations/preview"
                ).permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/auth/register", "/auth/register", "/api/auth/login", "/auth/login",
                    "/api/auth/google", "/auth/google", "/api/auth/forgot-password", "/auth/forgot-password",
                    "/api/auth/reset-password", "/auth/reset-password",
                    "/api/auth/apple/session", "/auth/apple/session", "/api/auth/apple/login", "/auth/apple/login"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new com.familyvault.auth.security.RequestRateLimitFilter(), JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration() {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(jwtAuthFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
