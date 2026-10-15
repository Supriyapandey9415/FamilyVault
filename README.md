# FamilyVault

Spring Boot 4 / Java 21 backend, React / Vite frontend, and MySQL database for family documents and memories.

## Local development

Requirements: Java 21, Maven, Node.js 22, and MySQL 8 with TLS enabled.
Configure the variables listed in [.env.example](.env.example) through your IDE or operating system.
Do not put real values in that example file. Restart the IDE after changing Windows user environment variables.

```sh
mvn spring-boot:run
```

From `familyvault-ui`:

```sh
npm ci --ignore-scripts
npm run dev
```

The backend defaults to loopback port 8080; the frontend uses localhost port 5173.
Local browser sessions use HttpOnly cookies and CSRF protection. Existing sessions and old reset links are invalidated by the security upgrade.

## Verification

```sh
mvn verify
```

Backend tests use an isolated H2 database and public test-only fixtures, not existing MySQL data.
Frontend checks: `npm run lint`, `npm run build`, and `npm audit --omit=dev`.
After packaging, `scripts/Test-BackendDependencies.ps1` checks public Maven component versions against OSV advisories.
Before publication, review and run `scripts/Test-PublishSafety.ps1`.

Read [security setup](docs/security-setup.md) before deployment or publication.
The Windows MySQL/firewall hardening script requires Administrator approval.
Local private files, backups, credentials, and generated build output must not be uploaded to GitHub.
