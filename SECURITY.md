# Security

Report vulnerabilities through the repository's private security reporting feature when enabled.
Do not include credentials, personal documents, reset links, or access tokens in public issues.

After reviewing the script, run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Test-PublishSafety.ps1` before publishing.
The execution-policy flag applies only to that process; it does not change Windows policy.
See [local setup and deployment requirements](docs/security-setup.md).

Security checks reduce known risks; they do not certify that a deployment has no vulnerabilities.
