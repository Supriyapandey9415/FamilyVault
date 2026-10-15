#requires -RunAsAdministrator
param(
    [string]$ConfigPath = 'C:\ProgramData\MySQL\MySQL Server 8.0\my.ini',
    [string]$ServerPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe',
    [string]$ServiceName = 'MySQL80'
)
$ErrorActionPreference = 'Stop'
$resultPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'target\mysql-hardening-result.json'
$result = @{ configuration = 'pending'; restart = 'pending'; firewall = 'pending' }
try {
    $resolvedConfig = [IO.Path]::GetFullPath($ConfigPath)
    if ($resolvedConfig -ne 'C:\ProgramData\MySQL\MySQL Server 8.0\my.ini') {
        throw 'Review the script before targeting another MySQL installation.'
    }
    $original = [IO.File]::ReadAllText($resolvedConfig)
    $section = [regex]::Match($original, '(?ms)^\[mysqld\]\r?\n.*?(?=^\[|\z)')
    if (-not $section.Success) { throw 'The mysqld configuration section was not found.' }
    $serverConfig = $section.Value
    foreach ($name in @('bind-address', 'mysqlx-bind-address')) {
        $pattern = '(?m)^\s*' + $name.Replace('-', '[-_]') + '\s*=.*$'
        if ([regex]::IsMatch($serverConfig, $pattern)) {
            $serverConfig = [regex]::Replace($serverConfig, $pattern, "$name=127.0.0.1")
        } else {
            $serverConfig += "`r`n$name=127.0.0.1`r`n"
        }
    }
    $updated = $original.Substring(0, $section.Index) + $serverConfig + $original.Substring($section.Index + $section.Length)
    $backup = "$resolvedConfig.familyvault.bak"
    if (-not (Test-Path -LiteralPath $backup)) { Copy-Item -LiteralPath $resolvedConfig -Destination $backup }
    # Reproducible administrator-only configuration operation; backup remains beside the original.
    [IO.File]::WriteAllText($resolvedConfig, $updated, [Text.UTF8Encoding]::new($false))
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $ServerPath "--defaults-file=$resolvedConfig" --validate-config 2>&1 | Out-Null
    $validationExit = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    if ($validationExit -ne 0) {
        [IO.File]::WriteAllText($resolvedConfig, $original, [Text.UTF8Encoding]::new($false))
        throw 'MySQL configuration validation failed; original configuration restored.'
    }
    $result.configuration = 'loopback-only'
    Restart-Service -Name $ServiceName
    $result.restart = 'completed'
    if (-not (Get-NetFirewallRule -DisplayName 'FamilyVault local MySQL only' -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName 'FamilyVault local MySQL only' -Direction Inbound -Action Block -Protocol TCP -LocalPort 3306,33060 -Profile Any | Out-Null
    }
    $result.firewall = 'inbound-blocked'
} catch {
    $result.error = $_.Exception.Message
} finally {
    if (-not (Test-Path (Split-Path -Parent $resultPath))) { New-Item -ItemType Directory -Path (Split-Path -Parent $resultPath) | Out-Null }
    $result | ConvertTo-Json | Set-Content -LiteralPath $resultPath -Encoding UTF8
}
if ($result.error) { throw $result.error }
Write-Output 'MySQL loopback bindings, service restart, and inbound firewall protection configured.'
