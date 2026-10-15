package com.familyvault.auth.service;

import com.familyvault.auth.dto.AuthConfigResponse;
import com.familyvault.auth.dto.GoogleLoginRequest;
import com.familyvault.auth.repository.UserRepository;
import com.familyvault.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserDetailsService userDetailsService;

    private GoogleAuthService googleAuthService;

    @BeforeEach
    void setUp() {
        googleAuthService = new GoogleAuthService(
                userRepository,
                jwtService,
                passwordEncoder,
                userDetailsService
        );
    }

    @Test
    void shouldRejectEmptyToken() {
        GoogleLoginRequest request = new GoogleLoginRequest("", null);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> googleAuthService.authenticateWithGoogle(request)
        );
        assertTrue(ex.getMessage().contains("cannot be empty"));
    }

    @Test
    void shouldRejectNullToken() {
        GoogleLoginRequest request = new GoogleLoginRequest(null, null);
        assertThrows(
                IllegalArgumentException.class,
                () -> googleAuthService.authenticateWithGoogle(request)
        );
    }

    @Test
    void shouldReturnDisabledConfigWhenClientIdNotSet() {
        AuthConfigResponse config = googleAuthService.getAuthConfig();
        assertNotNull(config);
        assertFalse(config.isGoogleEnabled());
    }

    @Test
    void shouldRejectTokensWhenServerClientIdIsMissing() {
        assertThrows(IllegalStateException.class,
                () -> googleAuthService.authenticateWithGoogle(new GoogleLoginRequest("not-a-real-token", null)));
        org.mockito.Mockito.verifyNoInteractions(userRepository);
    }

    @Test
    void verifiedGoogleEmailDoesNotSilentlyAdoptAnUnverifiedLocalAccount() {
        org.springframework.test.util.ReflectionTestUtils.setField(googleAuthService, "googleClientId", "test-client");
        var rest = (org.springframework.web.client.RestTemplate) org.springframework.test.util.ReflectionTestUtils.getField(googleAuthService, "restTemplate");
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(rest).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"iss\":\"https://accounts.google.com\",\"aud\":\"test-client\",\"sub\":\"google-subject\",\"email\":\"mailbox@example.com\",\"email_verified\":true}",
                        org.springframework.http.MediaType.APPLICATION_JSON));
        var local = new com.familyvault.auth.entity.User();
        local.setEmail("mailbox@example.com"); local.setPassword("existing-hash");
        org.mockito.Mockito.when(userRepository.findByEmail(local.getEmail())).thenReturn(java.util.Optional.of(local));
        assertThrows(IllegalArgumentException.class,
                () -> googleAuthService.authenticateWithGoogle(new GoogleLoginRequest("test-token", null)));
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
        assertNull(local.getGoogleId());
        server.verify();
    }
}
