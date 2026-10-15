package com.familyvault.auth.service;

import com.familyvault.auth.config.AppleAuthProperties;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

@Component
public class AppleClientSecretFactory {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final AppleAuthProperties properties;

    public AppleClientSecretFactory(AppleAuthProperties properties) {
        this.properties = properties;
    }

    public String createClientSecret() {
        try {
            Instant now = Instant.now();
            PrivateKey privateKey = loadPrivateKey();
            return Jwts.builder()
                    .header().keyId(properties.getKeyId().trim()).and()
                    .issuer(properties.getTeamId().trim())
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plus(5, ChronoUnit.MINUTES)))
                    .audience().add(APPLE_ISSUER).and()
                    .subject(properties.getClientId().trim())
                    .signWith(privateKey, Jwts.SIG.ES256)
                    .compact();
        } catch (Exception ex) {
            throw new IllegalStateException("Apple private key could not be loaded", ex);
        }
    }

    private PrivateKey loadPrivateKey() throws Exception {
        String pem;
        if (properties.getPrivateKeyBase64() != null && !properties.getPrivateKeyBase64().isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(properties.getPrivateKeyBase64().replaceAll("\\s", ""));
            pem = new String(decoded, StandardCharsets.UTF_8);
        } else {
            pem = Files.readString(Path.of(properties.getPrivateKeyPath().trim()), StandardCharsets.UTF_8);
        }

        String encodedKey = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }
}
