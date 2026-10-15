package com.familyvault.config;

import com.familyvault.config.entity.SmtpConfig;
import com.familyvault.config.repository.SmtpConfigRepository;
import com.familyvault.config.security.SecretCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SmtpSecretMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SmtpSecretMigrationRunner.class);

    private final SmtpConfigRepository repository;
    private final SecretCryptoService cryptoService;

    public SmtpSecretMigrationRunner(SmtpConfigRepository repository, SecretCryptoService cryptoService) {
        this.repository = repository;
        this.cryptoService = cryptoService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        repository.findFirstByOrderByIdAsc().ifPresent(config -> migrateIfNeeded(config));
    }

    private void migrateIfNeeded(SmtpConfig config) {
        String password = config.getPassword();
        if (password == null || password.isBlank() || cryptoService.isEncrypted(password)) {
            return;
        }
        if (!cryptoService.isConfigured()) {
            log.warn("[SMTP] A legacy plaintext credential exists. Set SMTP_CONFIG_ENCRYPTION_KEY to migrate it safely.");
            return;
        }
        config.setPassword(cryptoService.encrypt(password.replace(" ", "").trim()));
        repository.save(config);
        log.info("[SMTP] Legacy SMTP credential migrated to encrypted storage");
    }
}
