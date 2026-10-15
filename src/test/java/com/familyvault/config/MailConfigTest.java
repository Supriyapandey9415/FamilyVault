package com.familyvault.config;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MailConfigTest {

    @Test
    void shouldEnableAuthenticationOnlyWhenBothCredentialsAreConfigured() {
        MailConfig config = configuredMailConfig("user@gmail.com", "abcd efgh ijkl mnop");

        JavaMailSenderImpl sender = (JavaMailSenderImpl) config.javaMailSender();

        assertEquals("user@gmail.com", sender.getUsername());
        assertEquals("abcdefghijklmnop", sender.getPassword());
        assertEquals("true", sender.getJavaMailProperties().getProperty("mail.smtp.auth"));
        assertEquals("true", sender.getJavaMailProperties().getProperty("mail.smtp.ssl.enable"));
        assertEquals("false", sender.getJavaMailProperties().getProperty("mail.smtp.starttls.enable"));
    }

    @Test
    void shouldDisableAuthenticationWhenPasswordIsMissing() {
        MailConfig config = configuredMailConfig("user@gmail.com", "");

        JavaMailSenderImpl sender = (JavaMailSenderImpl) config.javaMailSender();

        assertEquals("false", sender.getJavaMailProperties().getProperty("mail.smtp.auth"));
    }

    private static MailConfig configuredMailConfig(String username, String password) {
        MailConfig config = new MailConfig();
        ReflectionTestUtils.setField(config, "host", "smtp.gmail.com");
        ReflectionTestUtils.setField(config, "port", 465);
        ReflectionTestUtils.setField(config, "username", username);
        ReflectionTestUtils.setField(config, "password", password);
        return config;
    }
}
