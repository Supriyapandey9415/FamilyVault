param(
    [string]$JarPath = (Join-Path (Split-Path -Parent $PSScriptRoot) 'target/familyvault.jar'),
    [string]$MavenRepository = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2/repository')
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$packages = @{}
$wanted = @{}
$archive = [IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($JarPath))
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -notlike 'BOOT-INF/lib/*.jar') { continue }
        $wanted[$entry.Name] = $true
        $memory = [IO.MemoryStream]::new()
        $stream = $entry.Open()
        try { $stream.CopyTo($memory) } finally { $stream.Dispose() }
        $memory.Position = 0
        $nested = [IO.Compression.ZipArchive]::new($memory, [IO.Compression.ZipArchiveMode]::Read)
        try {
            foreach ($properties in $nested.Entries) {
                if ($properties.FullName -notlike 'META-INF/maven/*/pom.properties') { continue }
                $reader = [IO.StreamReader]::new($properties.Open())
                try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
                $group = $null; $artifact = $null; $version = $null
                if ($content -match '(?m)^groupId=(.+)') { $group = $Matches[1].Trim() }
                if ($content -match '(?m)^artifactId=(.+)') { $artifact = $Matches[1].Trim() }
                if ($content -match '(?m)^version=(.+)') { $version = $Matches[1].Trim() }
                if ($group -and $artifact -and $version) {
                    $name = $group + ':' + $artifact
                    $packages["$name@$version"] = @{ package = @{ ecosystem = 'Maven'; name = $name }; version = $version }
                }
            }
        } finally { $nested.Dispose(); $memory.Dispose() }
    }
} finally { $archive.Dispose() }

# Some libraries (including server dependencies) omit embedded pom.properties.
# Match their packaged filenames against the local Maven repository coordinates as well.
if (Test-Path -LiteralPath $MavenRepository) {
    $repoRoot = [IO.Path]::GetFullPath($MavenRepository).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    Get-ChildItem -LiteralPath $repoRoot -Filter '*.jar' -File -Recurse | ForEach-Object {
        if ($wanted.ContainsKey($_.Name)) {
            $parts = $_.FullName.Substring($repoRoot.Length + 1).Replace('\','/').Split('/')
            if ($parts.Length -ge 4) {
                $artifact = $parts[$parts.Length - 3]
                $version = $parts[$parts.Length - 2]
                $group = ($parts[0..($parts.Length - 4)] -join '.')
                $name = $group + ':' + $artifact
                $packages["$name@$version"] = @{ package = @{ ecosystem = 'Maven'; name = $name }; version = $version }
            }
        }
    }
}
if ($packages.Count -eq 0) { throw 'No Maven components were identified; audit cannot pass.' }
$queries = @($packages.Values)
$body = @{ queries = $queries } | ConvertTo-Json -Depth 6 -Compress
$report = Invoke-RestMethod -Method Post -Uri 'https://api.osv.dev/v1/querybatch' -ContentType 'application/json' -Body $body -TimeoutSec 45
if ($report.results.Count -ne $queries.Count) { throw 'Incomplete advisory response; audit cannot pass.' }
$affected = 0
for ($i = 0; $i -lt $report.results.Count; $i++) {
    if ($report.results[$i].vulns) {
        $affected++
        Write-Output ($queries[$i].package.name + '@' + $queries[$i].version + ' : ' + (($report.results[$i].vulns | ForEach-Object { $_.id }) -join ', '))
    }
}
Write-Output ("Checked {0} Maven component versions from {1} packaged library files; affected components: {2}." -f $queries.Count, $wanted.Count, $affected)
if ($affected) { throw 'Known backend dependency advisories found. Review their applicability and patched versions.' }
Write-Output 'No known advisories returned for the checked component versions; this is not a complete security certification.'
