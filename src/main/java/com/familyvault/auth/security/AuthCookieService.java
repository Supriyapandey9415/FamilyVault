package com.familyvault.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class AuthCookieService {
    public static final String NAME = "FV_SESSION";
    private final boolean secure;
    private final String sameSite;
    private final long expiration;

    public AuthCookieService(@Value("${app.auth-cookie.secure:false}") boolean secure,
            @Value("${app.auth-cookie.same-site:Lax}") String sameSite,
            @Value("${application.security.jwt.expiration}") long expiration) {
        if (!java.util.Set.of("Strict", "Lax", "None").contains(sameSite) || ("None".equals(sameSite) && !secure)) {
            throw new IllegalStateException("Cookie SameSite=None requires Secure; use Strict, Lax, or None");
        }
        this.secure = secure;
        this.sameSite = sameSite;
        this.expiration = expiration;
    }

    public void issue(HttpServletResponse response, String token) { write(response, token, expiration); }
    public void clear(HttpServletResponse response) { write(response, "", 0); }

    private void write(HttpServletResponse response, String value, long milliseconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NAME, value)
                .httpOnly(true).secure(secure).sameSite(sameSite).path("/")
                .maxAge(Duration.ofMillis(milliseconds)).build().toString());
    }

    public String read(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (NAME.equals(cookie.getName())) { return cookie.getValue(); }
            }
        }
        return null;
    }
}
