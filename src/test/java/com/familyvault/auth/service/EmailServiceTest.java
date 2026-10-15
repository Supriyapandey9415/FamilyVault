package com.familyvault.auth.service;

import com.familyvault.config.repository.SmtpConfigRepository;
import com.familyvault.config.service.SmtpConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private SmtpConfigRepository smtpConfigRepository;

    @Mock
    private SmtpConfigService smtpConfigService;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, smtpConfigRepository, smtpConfigService);
        ReflectionTestUtils.setField(emailService, "mailUsername", "");
        ReflectionTestUtils.setField(emailService, "mailPassword", "");
    }

    @Test
    void shouldFailClearlyBeforeConnectingWhenApplicationCredentialsAreMissing() {
        when(smtpConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> emailService.sendPasswordResetEmail("member@example.com", "https://example.com/reset")
        );

        assertTrue(error.getMessage().contains("Check the server SMTP configuration"));
    }
}
