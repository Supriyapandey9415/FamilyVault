# Security setup and publishing

## Local Windows configuration

Spring Boot reads environment variables; it does not automatically load `.env.example`.
`scripts/Configure-LocalSecurity.ps1` sets Windows **user** variables without printing values.
Restart the IDE after configuration. Existing IDE-specific environment overrides take precedence;
remove stale overrides rather than copying secret values into project files.

Required: `JWT_SECRET_KEY`, `DB_USERNAME`, `DB_PASSWORD`.
Separate Flyway credentials: `DB_MIGRATION_USERNAME`, `DB_MIGRATION_PASSWORD`.
Optional integrations: `SMTP_USERNAME`, `SMTP_PASSWORD`, `GEMINI_API_KEY`, `GOOGLE_CLIENT_ID`.
Saved SMTP configuration additionally needs `SMTP_CONFIG_ENCRYPTION_KEY`.
JWT and SMTP encryption keys must be independently generated Base64-encoded 32-byte random values.
Keep the SMTP encryption key securely backed up: rotating it without re-encrypting stored data breaks decryption.
Never use a real application signing key as a test fixture.

The setup script can provision schema-scoped accounts with `-ProvisionDatabase`.
For subsequent runs, supply `DB_ADMIN_PASSWORD` only in the current PowerShell process;
the script does not save the database administrator password.
The application account has CRUD rights; the migration account has rights only on the FamilyVault schema.

The backend defaults to `127.0.0.1`. For the native, same-computer MySQL installation,
the local JDBC connection requires TLS and disables unauthenticated public-key retrieval.
Its `sslMode=REQUIRED` uses encryption with the local server's certificate; remote production
connections must use `sslMode=VERIFY_IDENTITY` and a trusted server CA.
run `scripts/Restrict-LocalMySql.ps1` from **Administrator PowerShell**.
It validates the config, keeps `my.ini.familyvault.bak`, restricts both database listeners,
restarts MySQL, and blocks inbound TCP 3306/33060. Its result is recorded only in ignored `target/`.
This machine-level step requires Windows administrator consent and is not implied by a successful application build.

## Authentication changes

Browser authentication uses a host-only HttpOnly cookie, with CSRF protection on modifying requests.
The frontend obtains a CSRF token from `GET /api/auth/csrf` and sends `X-XSRF-TOKEN`.
No login token or profile is stored in localStorage. Old cached credentials are removed on load.
Sessions have a fixed one-hour default expiry; sign in again after expiry (no refresh-token flow is implemented).
Logout revokes all sessions for that account. Password reset also revokes existing access and vault tokens.
Vault tokens have a separate purpose and PIN version; they cannot authenticate ordinary API requests.

Bearer authentication remains available for API clients. Obtain a token through normal login;
explicit bearer-authenticated mutations do not use browser cookies and do not require CSRF.
For unauthenticated login/reset calls in Postman, first get the CSRF token, retain its cookie,
and send the returned token in `X-XSRF-TOKEN`.

Direct password reset was removed. Reset links are random, hashed at rest, time-limited, and consumed under a database lock.
Migration V4 intentionally invalidates outstanding legacy reset links. Request new links after upgrading.
Migration V5 removes/redacts legacy notifications that exposed private document metadata.
Personal hidden documents are accessible only to their uploader; family ownership does not bypass this rule.
Hidden folders belong to their creator. Search and AI context exclude private documents while locked.
An unverified existing local account cannot silently link Google/Apple or obtain platform-admin access.
Use the emailed Forgot-password flow to prove mailbox control first; successful reset verifies the mailbox
and revokes previous sessions. A dedicated registration email-verification screen is not implemented.

## Before publishing

Run `scripts/Test-PublishSafety.ps1`. It checks actual Git inclusion rules (including already-tracked files),
known credential signatures, and active environment secrets without printing their values.
Before Git initialization it uses a disposable metadata directory and leaves the project uninitialized.
Never manually upload the entire workspace: it contains personal uploads and historical logs/builds.
Do not publish `uploads`, `target`, `node_modules`, local environment files, keys, certificates, logs, or backups.
Schema migrations are source code and should be published; personal database exports should not.
If credentials were ever exposed, revoke/rotate them first and separately review Git history, forks, and cached copies.
This workspace has no Git history until initialized; local scanning cannot inspect an existing remote repository.
Enable GitHub secret scanning, push protection, private vulnerability reporting, and branch protection after creating the repository.

## Production deployment requirements

- Use the `prod` Spring profile, HTTPS, and hosting-managed secrets. Never reuse local credentials.
  Production requires explicit `FRONTEND_URL` and `CORS_ALLOWED_ORIGINS`; no example domain is trusted by default.
- Prefer serving the UI and `/api` under the same site; `.env.production` assumes an `/api` reverse proxy.
  For separate sites, configure exact CORS origins and `AUTH_COOKIE_SAME_SITE=None` with `AUTH_COOKIE_SECURE=true`.
  Browsers may block third-party cookies; a same-site domain/proxy is the reliable deployment choice.
- The in-process rate limiter is bounded and suitable for one backend instance. Multiple replicas require shared gateway/Redis limits.
- Keep MySQL on a private network with authenticated TLS and separate runtime/migration accounts.
- Local storage is access-controlled but does not encrypt file bytes. Production sensitive documents require encrypted storage,
  backups/restore testing, retention policies, and malware scanning. Content-signature checks are not antivirus.
- Back up the database and file store consistently. Do not scale local file storage across independent replicas.
- Forwarded-header processing defaults to `none`. Only set `FORWARD_HEADERS_STRATEGY` after configuring
  a trusted reverse proxy that strips/replaces client-provided forwarding headers.
- OAuth provider configuration, account/email verification flows, provider reauthentication for social users' vault setup,
  and production infrastructure remain deployment responsibilities; no claim of complete security is made by passing tests.
