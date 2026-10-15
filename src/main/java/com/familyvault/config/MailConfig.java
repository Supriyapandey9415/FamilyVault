package com.familyvault.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String host;

    @Value("${spring.mail.port:465}")
    private int port;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${spring.mail.password:}")
    private String password;

    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        String cleanHost = host != null && !host.isBlank() ? host.trim() : "smtp.gmail.com";
        int cleanPort = port > 0 ? port : 465;
        boolean implicitTls = cleanPort == 465;
        mailSender.setHost(cleanHost);
        mailSender.setPort(cleanPort);

        String cleanUsername = username != null ? username.trim() : "";
        mailSender.setUsername(cleanUsername);

        // Strip any spaces from the password (Google App Passwords are 16 chars without spaces)
        String cleanPassword = password != null ? password.replace(" ", "").trim() : "";
        mailSender.setPassword(cleanPassword);

        boolean credentialsConfigured = !cleanUsername.isBlank() && !cleanPassword.isBlank();

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(credentialsConfigured));
        props.put("mail.smtp.starttls.enable", String.valueOf(!implicitTls));
        props.put("mail.smtp.starttls.required", String.valueOf(!implicitTls));
        props.put("mail.smtp.ssl.enable", String.valueOf(implicitTls));
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtp.connectiontimeout", "20000");
        props.put("mail.smtp.timeout", "20000");
        props.put("mail.smtp.writetimeout", "20000");

        log.info("[MAIL-CONFIG] Initialized SMTP transport on port {}", mailSender.getPort());
        if (!credentialsConfigured) {
            log.warn("[MAIL-CONFIG] SMTP authentication is disabled because SMTP_USERNAME or SMTP_PASSWORD is missing");
        }

        return mailSender;
    }
}
