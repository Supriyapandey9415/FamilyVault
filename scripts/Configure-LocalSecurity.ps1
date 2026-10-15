param(
    [string]$MysqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    [switch]$ProvisionDatabase
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function New-RandomSecret {
    $bytes = New-Object byte[] 32
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

function Get-ConfiguredValue([string]$Name) {
    foreach ($scope in @('Process', 'User')) {
        $value = [Environment]::GetEnvironmentVariable($Name, $scope)
        if (-not [string]::IsNullOrWhiteSpace($value)) { return $value }
    }
    return $null
}

function Invoke-LocalSql([string]$Username, [string]$Password, [string]$Sql) {
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $MysqlPath
    $start.Arguments = "--host=127.0.0.1 --port=3306 --user=$Username --batch --skip-column-names"
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.EnvironmentVariables['MYSQL_PWD'] = $Password
    $process = [Diagnostics.Process]::Start($start)
    $process.StandardInput.WriteLine($Sql)
    $process.StandardInput.Close()
    $result = $process.StandardOutput.ReadToEnd()
    $null = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw 'Local database operation failed. Credentials and SQL were withheld.' }
    $process.Dispose()
    return $result.Trim()
}

$values = @{}
$values['JWT_SECRET_KEY'] = Get-ConfiguredValue 'JWT_SECRET_KEY'
if (-not $values['JWT_SECRET_KEY']) { $values['JWT_SECRET_KEY'] = New-RandomSecret }
$values['SMTP_CONFIG_ENCRYPTION_KEY'] = Get-ConfiguredValue 'SMTP_CONFIG_ENCRYPTION_KEY'
foreach ($name in @('GEMINI_API_KEY', 'SMTP_USERNAME', 'SMTP_PASSWORD', 'GOOGLE_CLIENT_ID')) {
    $value = Get-ConfiguredValue $name
    if ($value) { $values[$name] = $value }
}

if ($ProvisionDatabase) {
    if (-not (Test-Path -LiteralPath $MysqlPath)) { throw 'MySQL client was not found.' }
    $adminName = Get-ConfiguredValue 'DB_ADMIN_USERNAME'
    if (-not $adminName) { $adminName = 'root' }
    $adminPassword = Get-ConfiguredValue 'DB_ADMIN_PASSWORD'
    if (-not $adminPassword) {
        # One-time migration from the old local config; never print or persist the admin credential.
        $oldConfig = Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'src/main/resources/application.yml')
        if ($oldConfig -match '\$\{DB_PASSWORD:([^}]+)\}') { $adminPassword = $Matches[1] }
    }
    if (-not $adminPassword) { throw 'Set DB_ADMIN_PASSWORD in this process to provision limited database accounts.' }
    $database = Get-ConfiguredValue 'DB_NAME'
    if (-not $database) { $database = 'familyvault' }
    if ($database -notmatch '^[A-Za-z0-9_]+$') { throw 'Unsupported database name.' }
    $encryptedCount = Invoke-LocalSql $adminName $adminPassword "SELECT COUNT(*) FROM $database.smtp_config WHERE password LIKE 'enc:v1:%';"
    if ($encryptedCount -ne '0' -and -not $values['SMTP_CONFIG_ENCRYPTION_KEY']) {
        throw 'Existing SMTP data is encrypted. Restore its existing encryption key before continuing.'
    }
    $appPassword = New-RandomSecret
    $migrationPassword = New-RandomSecret
    $sql = @"
CREATE USER IF NOT EXISTS 'familyvault_app'@'localhost' IDENTIFIED BY '$appPassword';
ALTER USER 'familyvault_app'@'localhost' IDENTIFIED BY '$appPassword';
GRANT SELECT, INSERT, UPDATE, DELETE ON $database.* TO 'familyvault_app'@'localhost';
CREATE USER IF NOT EXISTS 'familyvault_migrator'@'localhost' IDENTIFIED BY '$migrationPassword';
ALTER USER 'familyvault_migrator'@'localhost' IDENTIFIED BY '$migrationPassword';
GRANT ALL PRIVILEGES ON $database.* TO 'familyvault_migrator'@'localhost';
"@
    $null = Invoke-LocalSql $adminName $adminPassword $sql
    $values['DB_USERNAME'] = 'familyvault_app'
    $values['DB_PASSWORD'] = $appPassword
    $values['DB_MIGRATION_USERNAME'] = 'familyvault_migrator'
    $values['DB_MIGRATION_PASSWORD'] = $migrationPassword
    $values['DB_NAME'] = $database
    $null = Invoke-LocalSql 'familyvault_app' $appPassword "SELECT 1 FROM $database.users LIMIT 1;"
    Write-Output 'Limited application and schema-scoped migration accounts configured and verified.'
}

if (-not $values['SMTP_CONFIG_ENCRYPTION_KEY']) { $values['SMTP_CONFIG_ENCRYPTION_KEY'] = New-RandomSecret }
if (-not $values['GOOGLE_CLIENT_ID']) {
    $properties = Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'src/main/resources/application.properties')
    if ($properties -match '(?m)^GOOGLE_CLIENT_ID=(.+)$') { $values['GOOGLE_CLIENT_ID'] = $Matches[1].Trim() }
}
foreach ($name in $values.Keys) {
    [Environment]::SetEnvironmentVariable($name, $values[$name], 'User')
    [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
    Write-Output "$name configured (value hidden)."
}
Write-Output 'Restart the IDE before its next backend run. No secret values were written into the project.'
