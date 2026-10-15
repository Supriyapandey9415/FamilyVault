package com.familyvault.auth.controller;

import com.familyvault.auth.dto.AppleAuthConfigResponse;
import com.familyvault.auth.dto.AppleAuthSessionResponse;
import com.familyvault.auth.dto.AppleLoginRequest;
import com.familyvault.auth.dto.LoginResponse;
import com.familyvault.auth.service.AppleAuthService;
import com.familyvault.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/auth/apple", "/auth/apple"})
public class AppleAuthController {

    private final AppleAuthService appleAuthService;
    private final com.familyvault.auth.security.AuthCookieService cookies;

    public AppleAuthController(AppleAuthService appleAuthService, com.familyvault.auth.security.AuthCookieService cookies) {
        this.appleAuthService = appleAuthService;
        this.cookies = cookies;
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<AppleAuthConfigResponse>> getConfig() {
        return ResponseEntity.ok(ApiResponse.success(
                "Apple authentication configuration retrieved",
                appleAuthService.getConfig()
        ));
    }

    @PostMapping("/session")
    public ResponseEntity<ApiResponse<AppleAuthSessionResponse>> createSession() {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Apple authentication session created",
                    appleAuthService.createSession()
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody AppleLoginRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        try {
            LoginResponse login = appleAuthService.authenticate(request);
            cookies.issue(response, login.getToken());
            return ResponseEntity.ok(ApiResponse.success(
                    "Apple login successful",
                    login
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }
}
