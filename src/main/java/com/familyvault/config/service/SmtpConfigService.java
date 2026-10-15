package com.familyvault.config.service;

import com.familyvault.config.dto.SmtpConfigRequest;
import com.familyvault.config.dto.SmtpConfigResponse;
import com.familyvault.config.entity.SmtpConfig;
import com.familyvault.config.repository.SmtpConfigRepository;
import com.familyvault.config.security.PlatformAdminGuard;
import com.familyvault.config.security.SecretCryptoService;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Properties;

@Service
public class SmtpConfigService {

    private static final Logger log = LoggerFactory.getLogger(SmtpConfigService.class);

    private final SmtpConfigRepository smtpConfigRepository;
    private final PlatformAdminGuard platformAdminGuard;
    private final SecretCryptoService secretCryptoService;

    public SmtpConfigService(SmtpConfigRepository smtpConfigRepository,
                             PlatformAdminGuard platformAdminGuard,
                             SecretCryptoService secretCryptoService) {
        this.smtpConfigRepository = smtpConfigRepository;
        this.platformAdminGuard = platformAdminGuard;
        this.secretCryptoService = secretCryptoService;
    }

    public SmtpConfigResponse getCurrentConfig() {
        platformAdminGuard.requirePlatformAdmin();
        return smtpConfigRepository.findFirstByOrderByIdAsc()
                .map(SmtpConfigResponse::new)
                .orElse(null);
    }

    @Transactional
    public SmtpConfigResponse saveConfig(SmtpConfigRequest request) {
        platformAdminGuard.requirePlatformAdmin();
        if (request.getHost() == null || request.getHost().isBlank()) {
            throw new IllegalArgumentException("SMTP Host is required.");
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("SMTP Username / Email is required.");
        }

        Optional<SmtpConfig> existingOpt = smtpConfigRepository.findFirstByOrderByIdAsc();
        SmtpConfig config = existingOpt.orElseGet(SmtpConfig::new);

        String normalizedHost = normalizeHost(request.getHost());
        int normalizedPort = request.getPort() > 0 ? request.getPort() : 465;
        if (!isSupportedSmtpPort(normalizedPort)) {
            throw new IllegalArgumentException("SMTP port must be 25, 2525, 465, or 587.");
        }

        config.setHost(normalizedHost);
        config.setPort(normalizedPort);
        config.setUsername(request.getUsername().trim());

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            config.setPassword(secretCryptoService.encrypt(request.getPassword().replace(" ", "").trim()));
        } else if (config.getPassword() == null || config.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required for SMTP authentication.");
        }

        String fromEmail = (request.getFromEmail() != null && !request.getFromEmail().isBlank())
                ? request.getFromEmail().trim()
                : config.getUsername();
        config.setFromEmail(fromEmail);

        String fromName = (request.getFromName() != null && !request.getFromName().isBlank())
                ? request.getFromName().trim()
                : "FamilyVault Security";
        config.setFromName(fromName);

        config.setAuth(true);
        config.setStarttls(true);
        config.setActive(true);

        SmtpConfig saved = smtpConfigRepository.save(config);
        log.info("[SMTP] Dynamically updated SMTP configuration for host: {}, port: {}, username: {}",
                saved.getHost(), saved.getPort(), saved.getUsername());

        return new SmtpConfigResponse(saved);
    }

    public String testSmtpConnection(String testRecipient) {
        platformAdminGuard.requirePlatformAdmin();
        if (testRecipient == null || testRecipient.isBlank()) {
            throw new IllegalArgumentException("Recipient email address is required for test.");
        }

        SmtpConfig config = smtpConfigRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("No SMTP configuration saved yet. Please configure SMTP first."));

        if (!config.isActive() || config.getPassword() == null || config.getPassword().isBlank()) {
            throw new IllegalStateException("SMTP configuration is incomplete. Password is missing.");
        }

        JavaMailSenderImpl mailSender = createMailSender(config);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(config.getFromEmail(), config.getFromName());
            helper.setTo(testRecipient.trim());
            helper.setSubject("FamilyVault SMTP Connection Test - SUCCESS");

            String htmlBody = """
                <div style="font-family: Arial, sans-serif; padding: 20px; border: 1px solid #e2e8f0; border-radius: 12px; max-width: 500px;">
                    <h2 style="color: #4f46e5; margin-top: 0;">🎉 SMTP Test Successful!</h2>
                    <p style="color: #334155; font-size: 14px; line-height: 1.5;">
                        Your FamilyVault dynamic SMTP configuration is working perfectly.
                    </p>
                    <div style="background: #f8fafc; padding: 12px; border-radius: 8px; font-size: 13px; color: #475569;">
                        <strong>SMTP Host:</strong> %s<br/>
                        <strong>Port:</strong> %d<br/>
                        <strong>Sender:</strong> %s
                    </div>
                    <p style="color: #64748b; font-size: 12px; margin-top: 15px;">
                        Password reset codes and security notifications can now be dispatched to user inboxes.
                    </p>
                </div>
            """.formatted(config.getHost(), config.getPort(), config.getFromEmail());

            helper.setText(htmlBody, true);

            log.info("[SMTP TEST] Sending test email to {} using host {}", testRecipient, config.getHost());
            mailSender.send(message);
            log.info("[SMTP TEST] Test email sent successfully to {}", testRecipient);

            return "Test email successfully sent to " + testRecipient + "! Check your inbox.";
        } catch (Exception e) {
            log.error("[SMTP TEST ERROR] Failed to send test email to {}: {}", testRecipient, e.getMessage(), e);
            throw new IllegalArgumentException(
                "SMTP connection failed. Check the server settings and outbound network access."
            );
        }
    }

    public JavaMailSenderImpl createMailSender(SmtpConfig config) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        String host = normalizeHost(config.getHost());
        int configuredPort = config.getPort() > 0 ? config.getPort() : 465;
        int port = effectivePort(host, configuredPort);
        boolean implicitTls = port == 465;

        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(config.getUsername());
        // Google App Passwords are often pasted with spaces between groups.
        String decryptedPassword = secretCryptoService.decrypt(config.getPassword());
        mailSender.setPassword(decryptedPassword == null ? "" : decryptedPassword.replace(" ", "").trim());
        mailSender.setDefaultEncoding("UTF-8");

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(config.isAuth()));
        props.put("mail.smtp.starttls.enable", String.valueOf(config.isStarttls() && !implicitTls));
        props.put("mail.smtp.starttls.required", String.valueOf(config.isStarttls() && !implicitTls));
        props.put("mail.smtp.ssl.enable", String.valueOf(implicitTls));
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtp.connectiontimeout", "20000");
        props.put("mail.smtp.timeout", "20000");
        props.put("mail.smtp.writetimeout", "20000");

        return mailSender;
    }

    private static String normalizeHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase();
        return "gmail.com".equals(normalized) ? "smtp.gmail.com" : normalized;
    }

    /**
     * Gmail's implicit TLS endpoint is reachable in environments where the
     * STARTTLS endpoint on port 587 can hang while waiting for a response.
     * Keep the saved setting backward-compatible, but use the reliable Gmail
     * submission endpoint at runtime.
     */
    private static int effectivePort(String host, int configuredPort) {
        if ("smtp.gmail.com".equalsIgnoreCase(host) && configuredPort == 587) {
            log.warn("[SMTP] Gmail port 587 configured; using implicit TLS port 465 for reliable delivery");
            return 465;
        }
        return configuredPort;
    }

    private static boolean isSupportedSmtpPort(int port) {
        return port == 25 || port == 2525 || port == 465 || port == 587;
    }
}
