package com.familyvault.auth.service;

import com.familyvault.auth.config.AppleAuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AppleTokenVerifier {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_KEYS_URL = "https://appleid.apple.com/auth/keys";
    private static final Pattern KEY_ID_PATTERN = Pattern.compile("\\\"kid\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final AppleAuthProperties properties;
    private final RestTemplate restTemplate;

    private volatile Map<String, RSAPublicKey> cachedKeys = Collections.emptyMap();
    private volatile Instant keysExpireAt = Instant.EPOCH;

    public AppleTokenVerifier(AppleAuthProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restTemplate = new RestTemplate(factory);
    }

    public AppleIdentityClaims verify(String identityToken, String expectedNonce) {
        try {
            String keyId = readKeyId(identityToken);
            RSAPublicKey publicKey = resolveKey(keyId);
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .clockSkewSeconds(60)
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();

            if (!APPLE_ISSUER.equals(claims.getIssuer())) {
                throw new IllegalArgumentException("Apple identity token has an invalid issuer");
            }
            if (!audienceContains(claims.get("aud"), properties.getClientId().trim())) {
                throw new IllegalArgumentException("Apple identity token was not issued for this application");
            }

            String nonce = claims.get("nonce", String.class);
            if (!constantTimeEquals(expectedNonce, nonce)) {
                throw new IllegalArgumentException("Apple identity token nonce is invalid");
            }

            String subject = claims.getSubject();
            String email = claims.get("email", String.class);
            boolean emailVerified = asBoolean(claims.get("email_verified"));
            boolean privateEmail = asBoolean(claims.get("is_private_email"));
            if (subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("Apple identity token does not contain a user identifier");
            }
            if (email == null || email.isBlank() || !emailVerified) {
                throw new IllegalArgumentException("Apple did not provide a verified email address");
            }
            return new AppleIdentityClaims(subject.trim(), email.trim().toLowerCase(), true, privateEmail);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Apple identity token is invalid or expired", ex);
        }
    }

    private String readKeyId(String token) throws Exception {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Apple identity token is malformed");
        }
        byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
        String headerJson = new String(headerBytes, StandardCharsets.UTF_8);
        Matcher matcher = KEY_ID_PATTERN.matcher(headerJson);
        if (!matcher.find() || matcher.group(1).isBlank()) {
            throw new IllegalArgumentException("Apple identity token is missing its key identifier");
        }
        return matcher.group(1);
    }

    private RSAPublicKey resolveKey(String keyId) {
        Map<String, RSAPublicKey> snapshot = cachedKeys;
        if (Instant.now().isAfter(keysExpireAt) || !snapshot.containsKey(keyId)) {
            refreshKeys();
            snapshot = cachedKeys;
        }
        RSAPublicKey key = snapshot.get(keyId);
        if (key == null) {
            throw new IllegalArgumentException("Apple identity token uses an unknown signing key");
        }
        return key;
    }

    @SuppressWarnings("unchecked")
    private synchronized void refreshKeys() {
        if (Instant.now().isBefore(keysExpireAt) && !cachedKeys.isEmpty()) {
            return;
        }
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    APPLE_KEYS_URL,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            Object rawKeys = response.getBody() == null ? null : response.getBody().get("keys");
            if (!(rawKeys instanceof List<?> keyList)) {
                throw new IllegalStateException("Apple key response is empty");
            }

            Map<String, RSAPublicKey> parsed = new HashMap<>();
            for (Object rawKey : keyList) {
                if (!(rawKey instanceof Map<?, ?> key)) {
                    continue;
                }
                String kid = String.valueOf(key.get("kid"));
                String modulus = String.valueOf(key.get("n"));
                String exponent = String.valueOf(key.get("e"));
                if (kid.isBlank() || modulus.isBlank() || exponent.isBlank()) {
                    continue;
                }
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(modulus));
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(exponent));
                RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(n, e));
                parsed.put(kid, publicKey);
            }
            if (parsed.isEmpty()) {
                throw new IllegalStateException("Apple signing keys could not be parsed");
            }
            cachedKeys = Map.copyOf(parsed);
            keysExpireAt = Instant.now().plus(Duration.ofHours(6));
        } catch (Exception ex) {
            throw new IllegalStateException("Apple signing keys could not be retrieved", ex);
        }
    }

    private static boolean audienceContains(Object audience, String expected) {
        if (audience instanceof String value) {
            return expected.equals(value);
        }
        if (audience instanceof Collection<?> values) {
            return values.stream().anyMatch(expected::equals);
        }
        return false;
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static boolean asBoolean(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    public record AppleIdentityClaims(
            String subject,
            String email,
            boolean emailVerified,
            boolean privateEmail
    ) {}
}
