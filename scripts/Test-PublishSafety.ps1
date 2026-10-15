param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
$ProjectRoot = [IO.Path]::GetFullPath($ProjectRoot)
$temporaryRepository = $null
$issues = New-Object 'Collections.Generic.List[string]'
try {
    if (Test-Path -LiteralPath (Join-Path $ProjectRoot '.git')) {
        $files = @(& git -C $ProjectRoot ls-files --cached --others --exclude-standard)
    } else {
        # Apply real Git ignore rules without initializing or staging the user's project.
        $temporaryRepository = Join-Path ([IO.Path]::GetTempPath()) ('familyvault-publish-' + [guid]::NewGuid().ToString('N'))
        & git init --quiet --bare $temporaryRepository
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the disposable ignore-rule check.' }
        $files = @(& git "--git-dir=$temporaryRepository" "--work-tree=$ProjectRoot" ls-files --cached --others --exclude-standard)
    }
    $knownSecrets = @()
    foreach ($name in @('JWT_SECRET_KEY','DB_PASSWORD','DB_MIGRATION_PASSWORD','SMTP_PASSWORD','SMTP_CONFIG_ENCRYPTION_KEY','GEMINI_API_KEY','APPLE_PRIVATE_KEY_BASE64')) {
        foreach ($scope in @('Process','User')) {
            $value = [Environment]::GetEnvironmentVariable($name, $scope)
            if ($value -and $value.Length -ge 12) { $knownSecrets += $value }
        }
    }
    $forbidden = '(^|/)(uploads|target|node_modules|dist|backups|secrets|\.aws|\.ssh|\.codex)(/|$)|[^/]*(?:backup|dump)[^/]*\.sql(?:\.gz)?$|\.(p8|pem|key|pfx|p12|jks|keystore|dump|bak|jar|war|class|log|zip|7z|rar)$'
    foreach ($file in $files) {
        $relative = $file.Replace('\','/')
        if ($relative -match $forbidden) { $issues.Add("$relative : private data/build/key file is publishable"); continue }
        $leaf = [IO.Path]::GetFileName($relative)
        if ($leaf.StartsWith('.env') -and $leaf -ne '.env.example' -and $relative -ne 'familyvault-ui/.env.production') {
            $issues.Add("$relative : local environment file is publishable"); continue
        }
        $full = Join-Path $ProjectRoot $file
        if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { continue }
        if ([IO.Path]::GetExtension($file) -in '.png','.jpg','.jpeg','.webp','.gif','.ico','.woff','.woff2','.ttf') { continue }
        $content = [IO.File]::ReadAllText($full)
        if ($relative -notlike 'src/test/*' -and $relative.EndsWith('.sql') -and $content -match '(?i)INSERT\s+INTO\s+`?(users|documents|memories|smtp_config)`?\s*\(') {
            $issues.Add("$relative : personal database data export detected (content hidden)")
        }
        foreach ($secret in $knownSecrets) {
            if ($content.Contains($secret)) { $issues.Add("$relative : active environment secret detected (value hidden)"); break }
        }
        if ($content -cmatch 'AIza[0-9A-Za-z_-]{25,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|(?:AKIA|ASIA)[A-Z0-9]{16}|eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}') {
            $issues.Add("$relative : credential signature detected (value hidden)")
        }
        if ($content -match '(?s)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----\s+[A-Za-z0-9+/=\r\n]{40,}') {
            $issues.Add("$relative : private key material detected (value hidden)")
        }
        if ($relative -notlike 'src/test/*' -and ($file -match '\.(yml|yaml|properties)$' -or $leaf.StartsWith('.env'))) {
            foreach ($line in ($content -split '\r?\n')) {
                if ($line -match '^\s*[\w.-]*(?:password|secret-key|api\.key|encryption-key|private-key-base64)\s*[:=]\s*(.+)$') {
                    $value = $Matches[1].Trim()
                    if ($value -and -not $value.StartsWith('${') -and -not $value.StartsWith('#')) {
                        $issues.Add("$relative : literal secret configuration detected (value hidden)")
                    }
                }
            }
        }
    }
    if ($issues.Count) {
        $issues | Sort-Object -Unique | ForEach-Object { Write-Output $_ }
        throw 'Publication safety check failed. Nothing was staged, committed, or uploaded.'
    }
    Write-Output ("Publication check passed for {0} candidate files; no checked credential signatures, active environment secrets, or private/build files found." -f $files.Count)
    Write-Output 'This check is not a full dependency, binary, or Git history security audit.'
} finally {
    if ($temporaryRepository) {
        $resolved = [IO.Path]::GetFullPath($temporaryRepository)
        $temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        if ($resolved.StartsWith($temporaryRoot, [StringComparison]::OrdinalIgnoreCase) -and [IO.Path]::GetFileName($resolved) -like 'familyvault-publish-*') {
            Remove-Item -LiteralPath $resolved -Recurse -Force
        }
    }
}
