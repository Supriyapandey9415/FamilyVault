package com.familyvault.auth.service;

import com.familyvault.auth.dto.AuthConfigResponse;
import com.familyvault.auth.dto.GoogleLoginRequest;
import com.familyvault.auth.dto.LoginResponse;
import com.familyvault.auth.entity.AuthProvider;
import com.familyvault.auth.entity.User;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.auth.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
public class GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);
    private static final String GOOGLE_TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    private final RestTemplate restTemplate;

    @Value("${application.security.oauth2.google.client-id:}")
    private String googleClientId;

    public GoogleAuthService(
            UserRepository userRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            UserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restTemplate = new RestTemplate(factory);
    }

    public AuthConfigResponse getAuthConfig() {
        boolean enabled = googleClientId != null && !googleClientId.trim().isBlank();
        return new AuthConfigResponse(enabled ? googleClientId.trim() : "", enabled);
    }

    @Transactional
    public LoginResponse authenticateWithGoogle(GoogleLoginRequest request) {
        if (request == null || request.getIdToken() == null || request.getIdToken().trim().isBlank()) {
            throw new IllegalArgumentException("Google ID token cannot be empty");
        }
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new IllegalStateException("Google Sign-In is not configured on this server");
        }

        Map<String, Object> claims = verifyGoogleIdToken(request.getIdToken().trim());
        String email = ((String) claims.get("email"));
        if (email == null || email.trim().isBlank()) {
            throw new IllegalArgumentException("Google token did not contain a valid email address");
        }
        email = email.trim().toLowerCase();

        Object emailVerifiedObj = claims.get("email_verified");
        boolean emailVerified = "true".equalsIgnoreCase(String.valueOf(emailVerifiedObj))
                || Boolean.TRUE.equals(emailVerifiedObj);
        if (!emailVerified) {
            throw new IllegalArgumentException("Your Google email is not verified by Google");
        }

        String googleId = (String) claims.get("sub");
        if (googleId == null || googleId.isBlank()) {
            throw new IllegalArgumentException("Google token did not contain a valid subject");
        }
        String givenName = (String) claims.get("given_name");
        String familyName = (String) claims.get("family_name");
        String name = (String) claims.get("name");
        String picture = (String) claims.get("picture");

        String firstName = (givenName != null && !givenName.trim().isBlank())
                ? givenName.trim()
                : ((name != null && !name.trim().isBlank()) ? name.trim() : "Family");
        String lastName = (familyName != null && !familyName.trim().isBlank())
                ? familyName.trim()
                : "Member";

        final String finalEmail = email;
        final String finalGoogleId = googleId;
        final String finalPicture = picture;
        final String finalFirstName = firstName;
        final String finalLastName = lastName;

        User user = userRepository.findByEmail(finalEmail)
                .map(existingUser -> {
                    if (existingUser.getAuthProvider() == AuthProvider.LOCAL && !existingUser.isEmailVerified()) {
                        throw new IllegalArgumentException("Verify the existing account using Forgot password before linking Google Sign-In");
                    }
                    if (existingUser.getGoogleId() != null && !existingUser.getGoogleId().equals(finalGoogleId)) {
                        throw new IllegalArgumentException("This account is linked to a different Google identity");
                    }
                    boolean modified = false;
                    if (existingUser.getGoogleId() == null && finalGoogleId != null) {
                        existingUser.setGoogleId(finalGoogleId);
                        modified = true;
                    }
                    if (existingUser.getAvatarUrl() == null && finalPicture != null) {
                        existingUser.setAvatarUrl(finalPicture);
                        modified = true;
                    }
                    if (!existingUser.isEmailVerified()) {
                        existingUser.setEmailVerified(true);
                        modified = true;
                    }
                    return modified ? userRepository.save(existingUser) : existingUser;
                })
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(finalEmail);
                    newUser.setFirstName(finalFirstName);
                    newUser.setLastName(finalLastName);
                    newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                    newUser.setAuthProvider(AuthProvider.GOOGLE);
                    newUser.setGoogleId(finalGoogleId);
                    newUser.setAvatarUrl(finalPicture);
                    newUser.setEmailVerified(true);
                    return userRepository.save(newUser);
                });

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(userDetails);
        log.info("Google Sign-In completed");
        return new LoginResponse(token);
    }

    private Map<String, Object> verifyGoogleIdToken(String idToken) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(GOOGLE_TOKENINFO_URL)
                    .queryParam("id_token", idToken)
                    .build()
                    .toUri();

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> body = response.getBody();
            if (body == null || body.containsKey("error")) {
                String error = body != null ? String.valueOf(body.get("error_description")) : "Unknown error";
                throw new IllegalArgumentException("Google token verification failed: " + error);
            }

            // Verify Issuer
            String iss = (String) body.get("iss");
            if (iss == null || (!iss.equals("accounts.google.com") && !iss.equals("https://accounts.google.com"))) {
                throw new IllegalArgumentException("Google token has an invalid issuer: " + iss);
            }

            // Verify Audience if Google Client ID is configured
            if (googleClientId != null && !googleClientId.trim().isBlank()) {
                String aud = (String) body.get("aud");
                if (aud == null || !aud.trim().equals(googleClientId.trim())) {
                    log.warn("Audience mismatch: expected '{}', received '{}'", googleClientId, aud);
                    throw new IllegalArgumentException("Google token was not issued for this client ID");
                }
            }

            return body;
        } catch (RestClientResponseException e) {
            log.warn("Google token verification failed with status {}", e.getStatusCode());
            throw new IllegalArgumentException("Invalid or expired Google ID token");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Google token verification service is unavailable");
            throw new IllegalStateException("Google Sign-In is temporarily unavailable");
        }
    }
}
