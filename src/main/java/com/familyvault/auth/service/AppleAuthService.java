package com.familyvault.auth.service;

import com.familyvault.auth.config.AppleAuthProperties;
import com.familyvault.auth.dto.AppleAuthConfigResponse;
import com.familyvault.auth.dto.AppleAuthSessionResponse;
import com.familyvault.auth.dto.AppleLoginRequest;
import com.familyvault.auth.dto.LoginResponse;
import com.familyvault.auth.entity.AuthProvider;
import com.familyvault.auth.entity.User;
import com.familyvault.auth.entity.UserAuthIdentity;
import com.familyvault.auth.repository.UserAuthIdentityRepository;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.auth.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class AppleAuthService {

    private static final Logger log = LoggerFactory.getLogger(AppleAuthService.class);
    private static final String APPLE_TOKEN_URL = "https://appleid.apple.com/auth/token";

    private final AppleAuthProperties properties;
    private final AppleAuthSessionService sessionService;
    private final AppleClientSecretFactory clientSecretFactory;
    private final AppleTokenVerifier tokenVerifier;
    private final UserRepository userRepository;
    private final UserAuthIdentityRepository identityRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final RestTemplate restTemplate;
    private final TransactionTemplate transactionTemplate;

    public AppleAuthService(
            AppleAuthProperties properties,
            AppleAuthSessionService sessionService,
            AppleClientSecretFactory clientSecretFactory,
            AppleTokenVerifier tokenVerifier,
            UserRepository userRepository,
            UserAuthIdentityRepository identityRepository,
            PasswordEncoder passwordEncoder,
            UserDetailsService userDetailsService,
            JwtService jwtService,
            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.sessionService = sessionService;
        this.clientSecretFactory = clientSecretFactory;
        this.tokenVerifier = tokenVerifier;
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restTemplate = new RestTemplate(factory);
    }

    public AppleAuthConfigResponse getConfig() {
        boolean enabled = properties.isConfigured();
        return new AppleAuthConfigResponse(
                enabled,
                enabled ? properties.getClientId().trim() : "",
                enabled ? properties.getRedirectUri().trim() : ""
        );
    }

    public AppleAuthSessionResponse createSession() {
        requireConfigured();
        return sessionService.createSession();
    }

    public LoginResponse authenticate(AppleLoginRequest request) {
        requireConfigured();
        String expectedNonce = sessionService.consumeNonce(request.getState());
        String identityToken = exchangeAuthorizationCode(request.getAuthorizationCode().trim());
        AppleTokenVerifier.AppleIdentityClaims claims = tokenVerifier.verify(identityToken, expectedNonce);

        User user = transactionTemplate.execute(status -> identityRepository
                .findByProviderAndProviderSubject(AuthProvider.APPLE, claims.subject())
                .map(identity -> updateExistingIdentity(identity, claims))
                .orElseGet(() -> createOrLinkAppleIdentity(request, claims)));
        if (user == null) {
            throw new IllegalStateException("Apple account could not be linked");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails);
        log.info("Apple Sign-In successful for user id {}", user.getId());
        return new LoginResponse(token);
    }

    private User updateExistingIdentity(
            UserAuthIdentity identity,
            AppleTokenVerifier.AppleIdentityClaims claims) {
        identity.setProviderEmail(claims.email());
        identity.setEmailVerified(claims.emailVerified());
        identity.setLastLoginAt(LocalDateTime.now());
        identityRepository.save(identity);

        User user = identity.getUser();
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            return userRepository.save(user);
        }
        return user;
    }

    private User createOrLinkAppleIdentity(
            AppleLoginRequest request,
            AppleTokenVerifier.AppleIdentityClaims claims) {
        String email = claims.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .map(existing -> {
                    if (existing.getAuthProvider() == AuthProvider.LOCAL && !existing.isEmailVerified()) {
                        throw new IllegalArgumentException("Verify the existing account using Forgot password before linking Apple Sign-In");
                    }
                    if (!existing.isEmailVerified()) {
                        existing.setEmailVerified(true);
                        return userRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> createAppleUser(request, email));

        identityRepository.findByUserAndProvider(user, AuthProvider.APPLE).ifPresent(existing -> {
            throw new IllegalArgumentException("This FamilyVault account is already linked to another Apple account");
        });

        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setUser(user);
        identity.setProvider(AuthProvider.APPLE);
        identity.setProviderSubject(claims.subject());
        identity.setProviderEmail(email);
        identity.setEmailVerified(true);
        identity.setLastLoginAt(LocalDateTime.now());
        identityRepository.save(identity);
        return user;
    }

    private User createAppleUser(AppleLoginRequest request, String email) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName(cleanName(request.getFirstName(), "Family"));
        user.setLastName(cleanName(request.getLastName(), "Member"));
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setAuthProvider(AuthProvider.APPLE);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private String exchangeAuthorizationCode(String authorizationCode) {
        if (authorizationCode.isBlank()) {
            throw new IllegalArgumentException("Apple authorization code is required");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getClientId().trim());
        form.add("client_secret", clientSecretFactory.createClientSecret());
        form.add("code", authorizationCode);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", properties.getRedirectUri().trim());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    APPLE_TOKEN_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    new ParameterizedTypeReference<>() {}
            );
            Map<String, Object> body = response.getBody();
            if (body == null || body.get("id_token") == null) {
                throw new IllegalArgumentException("Apple did not return a valid identity token");
            }
            return String.valueOf(body.get("id_token"));
        } catch (RestClientResponseException ex) {
            log.warn("Apple authorization-code exchange failed with status {}", ex.getStatusCode());
            throw new IllegalArgumentException("Apple authorization code is invalid or expired");
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("Apple Sign-In is not configured");
        }
    }

    private static String cleanName(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String clean = value.replaceAll("[\\p{Cntrl}]", "").trim();
        if (clean.isEmpty()) {
            return fallback;
        }
        return clean.length() > 100 ? clean.substring(0, 100) : clean;
    }
}
