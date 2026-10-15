package com.familyvault.auth.service;

import com.familyvault.auth.dto.AppleAuthSessionResponse;
import com.familyvault.auth.entity.AppleAuthSession;
import com.familyvault.auth.repository.AppleAuthSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppleAuthSessionServiceTest {

    @Mock
    private AppleAuthSessionRepository repository;

    private AppleAuthSessionService service;

    @BeforeEach
    void setUp() {
        service = new AppleAuthSessionService(repository);
    }

    @Test
    void shouldStoreOnlyHashedState() {
        AppleAuthSessionResponse response = service.createSession();

        ArgumentCaptor<AppleAuthSession> captor = ArgumentCaptor.forClass(AppleAuthSession.class);
        verify(repository).save(captor.capture());
        AppleAuthSession saved = captor.getValue();

        assertNotEquals(response.state(), saved.getStateHash());
        assertEquals(64, saved.getStateHash().length());
        assertEquals(response.nonce(), saved.getNonce());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void shouldConsumeSessionOnlyOnce() {
        AppleAuthSession session = new AppleAuthSession();
        session.setStateHash("hash");
        session.setNonce("expected-nonce");
        session.setExpiresAt(LocalDateTime.now().plusMinutes(1));
        when(repository.findForUpdate(any())).thenReturn(Optional.of(session));

        assertEquals("expected-nonce", service.consumeNonce("raw-state"));
        verify(repository).delete(session);
    }

    @Test
    void shouldRejectExpiredSession() {
        AppleAuthSession session = new AppleAuthSession();
        session.setStateHash("hash");
        session.setNonce("nonce");
        session.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(repository.findForUpdate(any())).thenReturn(Optional.of(session));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.consumeNonce("raw-state")
        );
        assertTrue(error.getMessage().contains("expired"));
    }
}
