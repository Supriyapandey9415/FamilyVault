package com.familyvault.auth.controller;

import com.familyvault.auth.dto.AuthConfigResponse;
import com.familyvault.auth.dto.ForgotPasswordRequest;
import com.familyvault.auth.dto.GoogleLoginRequest;
import com.familyvault.auth.dto.LoginRequest;
import com.familyvault.auth.dto.LoginResponse;
import com.familyvault.auth.dto.RegisterRequest;
import com.familyvault.auth.dto.ResetPasswordRequest;
import com.familyvault.auth.dto.UserResponse;
import com.familyvault.auth.security.JwtService;
import com.familyvault.auth.service.AuthService;
import com.familyvault.auth.service.GoogleAuthService;
import com.familyvault.auth.service.PasswordResetService;
import com.familyvault.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/auth", "/auth"})
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final GoogleAuthService googleAuthService;
    private final com.familyvault.auth.security.AuthCookieService cookies;

    public AuthController(AuthService authService,
                          PasswordResetService passwordResetService,
                          AuthenticationManager authenticationManager,
                          JwtService jwtService,
                          UserDetailsService userDetailsService,
                          GoogleAuthService googleAuthService,
                          com.familyvault.auth.security.AuthCookieService cookies) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.googleAuthService = googleAuthService;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            UserResponse response = authService.register(request);
            return ResponseEntity.ok(ApiResponse.success("User registered successfully", response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        String token = jwtService.generateToken(userDetails);
        cookies.issue(response, token);
        return ResponseEntity.ok(ApiResponse.success("Login successful", new LoginResponse(token)));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<LoginResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest request,
            jakarta.servlet.http.HttpServletResponse servletResponse) {
        try {
            LoginResponse response = googleAuthService.authenticateWithGoogle(request);
            cookies.issue(servletResponse, response.getToken());
            return ResponseEntity.ok(ApiResponse.success("Google login successful", response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<AuthConfigResponse>> getAuthConfig() {
        AuthConfigResponse response = googleAuthService.getAuthConfig();
        return ResponseEntity.ok(ApiResponse.success("Authentication configuration retrieved", response));
    }

    @GetMapping("/csrf")
    public java.util.Map<String, String> csrf(org.springframework.security.web.csrf.CsrfToken token) {
        return java.util.Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(jakarta.servlet.http.HttpServletResponse response) {
        authService.revokeCurrentSessions();
        cookies.clear(response);
        return ResponseEntity.ok(ApiResponse.success("Signed out", null));
    }

    @org.springframework.web.bind.annotation.GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Not authenticated"));
        }
        UserResponse response = authService.getCurrentUser(auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully", response));
    }

    @org.springframework.web.bind.annotation.PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(@RequestBody java.util.Map<String, String> request) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Not authenticated"));
        }
        String firstName = request.get("firstName");
        String lastName = request.get("lastName");
        UserResponse response = authService.updateProfile(auth.getName(), firstName, lastName);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.createPasswordResetTokenAndSendEmail(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("If an account exists with that email, a password reset link has been sent to your email.", null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPasswordWithToken(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully. You can now login with your new password.", null));
    }

    @GetMapping("/verify-reset-token")
    public ResponseEntity<ApiResponse<Void>> verifyResetToken(@RequestParam("token") String token) {
        passwordResetService.validateTokenOnly(token);
        return ResponseEntity.ok(ApiResponse.success("Reset link is valid.", null));
    }
}
