package com.familyvault.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    @Value("${application.security.jwt.vault-expiration:600000}")
    private long vaultExpiration;

    @PostConstruct
    void validateConfiguration() {
        try {
            if (getSignInKey().getEncoded().length < 32 || jwtExpiration <= 0 || vaultExpiration <= 0) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("JWT_SECRET_KEY must be a Base64-encoded random key of at least 32 bytes; token durations must be positive");
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("tokenType", "ACCESS");
        claims.put("securityVersion", versionOf(userDetails));
        return generateToken(claims, userDetails.getUsername(), jwtExpiration);
    }

    public String generateVaultToken(String username, Long familyId) {
        return generateVaultToken(username, familyId, 0);
    }

    public String generateVaultToken(String username, Long familyId, long securityVersion) {
        return generateVaultToken(username, familyId, securityVersion, 0);
    }

    public String generateVaultToken(String username, Long familyId, long securityVersion, long vaultVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "VAULT");
        claims.put("familyId", familyId);
        claims.put("securityVersion", securityVersion);
        claims.put("vaultVersion", vaultVersion);
        return generateToken(claims, username, vaultExpiration);
    }

    private String generateToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuer("familyvault")
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractAllClaims(token);
        return userDetails.getUsername().equals(claims.getSubject())
                && "ACCESS".equals(claims.get("tokenType", String.class))
                && versionMatches(claims, versionOf(userDetails))
                && claims.getExpiration() != null && claims.getExpiration().after(new Date());
    }

    public boolean isVaultTokenValid(String token, String username, Long familyId) {
        return isVaultTokenValid(token, username, familyId, 0);
    }

    public boolean isVaultTokenValid(String token, String username, Long familyId, long securityVersion) {
        return isVaultTokenValid(token, username, familyId, securityVersion, 0);
    }

    public boolean isVaultTokenValid(String token, String username, Long familyId, long securityVersion, long vaultVersion) {
        Claims claims = extractAllClaims(token);
        Object familyClaim = claims.get("familyId");
        long tokenFamilyId = familyClaim instanceof Number number
                ? number.longValue()
                : Long.parseLong(String.valueOf(familyClaim));
        return username.equals(claims.getSubject())
                && "VAULT".equals(claims.get("tokenType", String.class))
                && versionMatches(claims, securityVersion)
                && claims.get("vaultVersion") instanceof Number pinVersion && pinVersion.longValue() == vaultVersion
                && familyId != null
                && familyId == tokenFamilyId
                && claims.getExpiration().after(new Date());
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .requireIssuer("familyvault")
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private long versionOf(UserDetails details) {
        return details instanceof SessionUserDetails session ? session.getSecurityVersion() : 0;
    }

    private boolean versionMatches(Claims claims, long expected) {
        return claims.get("securityVersion") instanceof Number number && number.longValue() == expected;
    }
}
