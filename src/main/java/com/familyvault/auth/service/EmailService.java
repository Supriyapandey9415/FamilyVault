package com.familyvault.auth.service;

import com.familyvault.config.entity.SmtpConfig;
import com.familyvault.config.repository.SmtpConfigRepository;
import com.familyvault.config.service.SmtpConfigService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final SmtpConfigRepository smtpConfigRepository;
    private final SmtpConfigService smtpConfigService;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public EmailService(JavaMailSender mailSender,
                        SmtpConfigRepository smtpConfigRepository,
                        SmtpConfigService smtpConfigService) {
        this.mailSender = mailSender;
        this.smtpConfigRepository = smtpConfigRepository;
        this.smtpConfigService = smtpConfigService;
    }

    /**
     * Sends a secure password reset email with the reset link.
     */
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        try {
            MailContext mailContext = resolveMailContext();
            MimeMessage message = mailContext.sender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailContext.fromEmail(), mailContext.fromName());
            helper.setTo(toEmail);
            helper.setSubject("Reset Your FamilyVault Password");

            String htmlContent = buildResetLinkHtml(resetLink);
            helper.setText(htmlContent, true);

            log.info("[EMAIL] Sending password reset email");
            mailContext.sender().send(message);
            log.info("[EMAIL] Password reset email sent");

        } catch (MessagingException e) {
            log.warn("[EMAIL] Password reset email delivery failed");
            throw new IllegalStateException("Email delivery is unavailable. Check the server SMTP configuration.");
        } catch (Exception e) {
            log.warn("[EMAIL] Password reset email delivery failed");
            throw new IllegalStateException("Email delivery is unavailable. Check the server SMTP configuration.");
        }
    }

    public void sendFamilyInvitationEmail(String toEmail,
                                          String familyName,
                                          String inviterName,
                                          String role,
                                          String invitationLink,
                                          int validDays) {
        try {
            MailContext mailContext = resolveMailContext();
            MimeMessage message = mailContext.sender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailContext.fromEmail(), mailContext.fromName());
            helper.setTo(toEmail);
            helper.setSubject(inviterName + " invited you to join " + familyName + " on FamilyVault");
            helper.setText(buildFamilyInvitationHtml(familyName, inviterName, role, invitationLink, validDays), true);

            log.info("[EMAIL] Sending family invitation");
            mailContext.sender().send(message);
            log.info("[EMAIL] Family invitation sent");
        } catch (MessagingException e) {
            log.warn("[EMAIL] Family invitation delivery failed");
            throw new IllegalStateException("Invitation email delivery is unavailable. Check the server SMTP configuration.");
        } catch (Exception e) {
            log.warn("[EMAIL] Family invitation delivery failed");
            throw new IllegalStateException("Invitation email delivery is unavailable. Check the server SMTP configuration.");
        }
    }

    private MailContext resolveMailContext() {
        Optional<SmtpConfig> savedConfig = smtpConfigRepository.findFirstByOrderByIdAsc();
        if (savedConfig.isPresent() && isUsable(savedConfig.get())) {
            SmtpConfig config = savedConfig.get();
            log.debug("[MAIL-CONFIG] Using saved SMTP configuration {}:{}", config.getHost(), config.getPort());
            return new MailContext(
                    smtpConfigService.createMailSender(config),
                    config.getFromEmail(),
                    config.getFromName()
            );
        }

        if (savedConfig.isPresent()) {
            log.warn("[MAIL-CONFIG] Saved SMTP configuration is incomplete or unsupported; using application SMTP settings");
        }

        String defaultFrom = (mailUsername != null && !mailUsername.isBlank() && !mailUsername.contains("YOUR_GMAIL_HERE"))
                ? mailUsername : "noreply@familyvault.com";
        if (!hasText(mailUsername) || !hasText(mailPassword)) {
            throw new IllegalStateException(
                    "SMTP authentication is not configured. Set SMTP_USERNAME and SMTP_PASSWORD, "
                            + "then restart the application."
            );
        }
        return new MailContext(mailSender, defaultFrom, "FamilyVault Security");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isUsable(SmtpConfig config) {
        return config.isActive()
                && config.getHost() != null && !config.getHost().isBlank()
                && config.getUsername() != null && !config.getUsername().isBlank()
                && config.getPassword() != null && !config.getPassword().isBlank()
                && (config.getPort() == 25 || config.getPort() == 2525
                || config.getPort() == 465 || config.getPort() == 587);
    }

    private record MailContext(JavaMailSender sender, String fromEmail, String fromName) {
    }

    private String buildFamilyInvitationHtml(String familyName,
                                              String inviterName,
                                              String role,
                                              String invitationLink,
                                              int validDays) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>FamilyVault Invitation</title>
            </head>
            <body style="margin:0;padding:0;background:#f1f5f9;font-family:Arial,sans-serif;color:#0f172a;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 16px;background:#f1f5f9;">
                    <tr><td align="center">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#fff;border-radius:18px;overflow:hidden;border:1px solid #e2e8f0;">
                            <tr><td style="padding:32px;text-align:center;background:linear-gradient(135deg,#312e81,#6366f1);">
                                <h1 style="margin:0;color:#fff;font-size:26px;">FamilyVault</h1>
                                <p style="margin:8px 0 0;color:#e0e7ff;">A private space for your family</p>
                            </td></tr>
                            <tr><td style="padding:36px;">
                                <h2 style="margin:0 0 16px;font-size:22px;">You have been invited</h2>
                                <p style="font-size:15px;line-height:1.6;color:#475569;">%s invited you to join the <strong>%s</strong> family vault as a <strong>%s</strong>.</p>
                                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin:28px 0;"><tr><td align="center">
                                    <a href="%s" style="display:inline-block;padding:15px 28px;border-radius:10px;background:#4f46e5;color:#fff;text-decoration:none;font-weight:700;">View Invitation &amp; Join Family</a>
                                </td></tr></table>
                                <p style="font-size:13px;line-height:1.5;color:#64748b;">This invitation expires in %d days and can only be used with the invited email address. If you were not expecting this invitation, you can safely ignore this email.</p>
                            </td></tr>
                            <tr><td style="padding:20px 36px;border-top:1px solid #e2e8f0;text-align:center;color:#94a3b8;font-size:12px;">This is an automated FamilyVault email.</td></tr>
                        </table>
                    </td></tr>
                </table>
            </body>
            </html>
            """.formatted(escapeHtml(inviterName), escapeHtml(familyName), escapeHtml(role), invitationLink, validDays);
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String buildResetLinkHtml(String resetLink) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Reset Your Password - FamilyVault</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased;">
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color: #f1f5f9; padding: 40px 16px;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width: 560px; background-color: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08); border: 1px solid #e2e8f0;">
                                <!-- Header Banner with Gradient Accent -->
                                <tr>
                                    <td style="background: linear-gradient(135deg, #1e1b4b 0%, #312e81 50%, #4338ca 100%); padding: 36px 32px; text-align: center;">
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center" style="margin: 0 auto 12px auto;">
                                            <tr>
                                                <td style="background: rgba(255, 255, 255, 0.15); border: 1px solid rgba(255, 255, 255, 0.25); border-radius: 14px; width: 48px; height: 48px; text-align: center; vertical-align: middle; font-size: 24px;">
                                                    🛡️
                                                </td>
                                            </tr>
                                        </table>
                                        <h1 style="margin: 0 0 4px 0; font-size: 24px; font-weight: 800; color: #ffffff; letter-spacing: -0.5px;">FamilyVault</h1>
                                        <p style="margin: 0; font-size: 13px; font-weight: 500; color: #c7d2fe; letter-spacing: 0.5px; text-transform: uppercase;">Secure Family Cloud Storage</p>
                                    </td>
                                </tr>

                                <!-- Main Content Body -->
                                <tr>
                                    <td style="padding: 40px 36px 32px 36px;">
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin-bottom: 20px;">
                                            <tr>
                                                <td style="background-color: #eef2ff; border: 1px solid #c7d2fe; border-radius: 20px; padding: 6px 14px;">
                                                    <span style="font-size: 12px; font-weight: 700; color: #4338ca; text-transform: uppercase; letter-spacing: 0.8px;">🔐 Account Recovery</span>
                                                </td>
                                            </tr>
                                        </table>

                                        <h2 style="margin: 0 0 14px 0; font-size: 22px; font-weight: 700; color: #0f172a; letter-spacing: -0.4px;">
                                            Password Reset Request
                                        </h2>

                                        <p style="margin: 0 0 20px 0; font-size: 15px; line-height: 1.6; color: #475569;">
                                            Hello,
                                        </p>
                                        <p style="margin: 0 0 28px 0; font-size: 15px; line-height: 1.65; color: #475569;">
                                            We received a request to reset the password for your <strong>FamilyVault</strong> account. Click the button below to securely set your new password:
                                        </p>

                                        <!-- CTA Button Section -->
                                        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin: 32px 0;">
                                            <tr>
                                                <td align="center">
                                                    <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                                                        <tr>
                                                            <td align="center" style="background: linear-gradient(135deg, #4f46e5 0%, #6366f1 50%, #7c3aed 100%); border-radius: 12px; box-shadow: 0 6px 20px rgba(79, 70, 229, 0.35);">
                                                                <a href="{{RESET_LINK}}" target="_blank" style="display: inline-block; padding: 16px 36px; font-size: 15px; font-weight: 700; color: #ffffff !important; text-decoration: none; border-radius: 12px; letter-spacing: 0.3px;">
                                                                    Reset Password &rarr;
                                                                </a>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                        </table>

                                        <!-- Expiry Alert Box -->
                                        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color: #f8fafc; border-left: 4px solid #6366f1; border-radius: 8px; margin: 28px 0 24px 0;">
                                            <tr>
                                                <td style="padding: 16px 18px;">
                                                    <p style="margin: 0 0 6px 0; font-size: 13px; font-weight: 700; color: #1e1b4b;">
                                                        ⏰ Link Valid For 15 Minutes
                                                    </p>
                                                    <p style="margin: 0; font-size: 13px; line-height: 1.5; color: #64748b;">
                                                        For your account security, this password reset link will expire automatically in <strong>15 minutes</strong>. If you did not request a password reset, you can safely ignore this email—your account remains strictly protected.
                                                    </p>
                                                </td>
                                            </tr>
                                        </table>

                                    </td>
                                </tr>

                                <!-- Footer Section -->
                                <tr>
                                    <td style="background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 24px 36px; text-align: center;">
                                        <table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center" style="margin: 0 auto 10px auto;">
                                            <tr>
                                                <td style="font-size: 12px; color: #94a3b8; padding: 0 8px;">🛡️ End-to-End Encrypted</td>
                                                <td style="font-size: 12px; color: #cbd5e1;">&bull;</td>
                                                <td style="font-size: 12px; color: #94a3b8; padding: 0 8px;">🔒 Zero-Knowledge Security</td>
                                            </tr>
                                        </table>
                                        <p style="margin: 0 0 6px 0; font-size: 12px; color: #94a3b8;">
                                            &copy; {{YEAR}} FamilyVault Inc. All rights reserved.
                                        </p>
                                        <p style="margin: 0; font-size: 11px; color: #cbd5e1;">
                                            This is an automated security transmission. Please do not reply directly to this email.
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """
            .replace("{{RESET_LINK}}", resetLink)
            .replace("{{YEAR}}", String.valueOf(java.time.Year.now().getValue()));
    }
}
