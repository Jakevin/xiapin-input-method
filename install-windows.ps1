[CmdletBinding()]
param(
    [string]$RimeUserDir,
    [string]$DeployerPath,
    [switch]$NoDeploy,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$Timestamp = Get-Date -Format "yyyyMMddHHmmssfff"

function Get-RimeUserDirectory {
    if ($RimeUserDir) {
        return [Environment]::ExpandEnvironmentVariables($RimeUserDir)
    }
    try {
        $settings = Get-ItemProperty -LiteralPath "HKCU:\Software\Rime\Weasel" -ErrorAction Stop
        if ($settings.RimeUserDir) {
            return [Environment]::ExpandEnvironmentVariables([string]$settings.RimeUserDir)
        }
    }
    catch {
        # Weasel has not recorded a custom user directory.
    }
    if (-not $env:APPDATA) {
        throw "APPDATA is not set. Pass -RimeUserDir with an explicit test or install directory."
    }
    return Join-Path $env:APPDATA "Rime"
}

function Backup-File([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return }
    $backup = "$Path.bak.$Timestamp"
    if ($DryRun) {
        Write-Host "[dry-run] Backup $Path -> $backup"
    }
    else {
        Copy-Item -LiteralPath $Path -Destination $backup
    }
}

function Copy-XiapinFile([string]$RelativePath, [string]$DestinationRelativePath) {
    $source = Join-Path $Root $RelativePath
    $destination = Join-Path $script:TargetDir $DestinationRelativePath
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Package file is missing: $source"
    }
    Backup-File $destination
    if ($DryRun) {
        Write-Host "[dry-run] Copy $RelativePath -> $destination"
    }
    else {
        $parent = Split-Path -Parent $destination
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination -Force
    }
}

function Add-SchemasToDefaultCustom([string]$Path) {
    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        $text = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8)
        Backup-File $Path
    }
    else {
        $text = "patch:`n"
    }

    $newline = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $lines = New-Object 'System.Collections.Generic.List[string]'
    foreach ($line in ($text -split "`r?`n")) { [void]$lines.Add($line) }

    $schemaIndex = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^\s{2}schema_list:\s*$') {
            $schemaIndex = $i
            break
        }
    }
    if ($schemaIndex -lt 0) {
        if (-not ($lines | Where-Object { $_ -match '^patch:\s*$' })) {
            [void]$lines.Add("patch:")
        }
        while ($lines.Count -gt 0 -and $lines[$lines.Count - 1] -eq "") {
            $lines.RemoveAt($lines.Count - 1)
        }
        [void]$lines.Add("  schema_list:")
        $schemaIndex = $lines.Count - 1
    }

    $insertIndex = $schemaIndex + 1
    while ($insertIndex -lt $lines.Count) {
        $line = $lines[$insertIndex]
        if ($line -match '^\s{0,2}\S' -and $line -notmatch '^\s*#') { break }
        $insertIndex++
    }
    foreach ($schema in @("xiapin", "xiapin_english")) {
        $marker = "- schema: $schema"
        if (-not ($lines | Where-Object { $_.Trim() -eq $marker })) {
            $lines.Insert($insertIndex, "    $marker")
            $insertIndex++
        }
    }
    while ($lines.Count -gt 0 -and $lines[$lines.Count - 1] -eq "") {
        $lines.RemoveAt($lines.Count - 1)
    }
    $updated = [string]::Join($newline, $lines) + $newline
    if ($DryRun) {
        Write-Host "[dry-run] Add xiapin and xiapin_english to $Path"
    }
    else {
        [IO.File]::WriteAllText($Path, $updated, $Utf8NoBom)
    }
}

function Build-XiapinLiurDictionary([string]$Destination) {
    $sources = @(
        (Join-Path $Root "rime\openxiami_TCJP.dict.yaml"),
        (Join-Path $Root "rime\openxiami_TradExt.dict.yaml")
    ) | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf }
    if ($sources.Count -eq 0) {
        Write-Warning "openxiami dictionaries were not found; optional root table was not generated."
        return $false
    }
    if ($DryRun) {
        Write-Host "[dry-run] Generate filtered dictionary -> $Destination"
        return $true
    }

    $lines = New-Object 'System.Collections.Generic.List[string]'
    foreach ($line in @(
        "# Rime dictionary",
        "# encoding: utf-8",
        "# Local weighted import generated from openxiami dictionaries.",
        "# Source: https://github.com/ryanwuson/rime-liur",
        "---",
        "name: xiapin_liur",
        'version: "1-local"',
        "sort: by_weight",
        "..."
    )) { [void]$lines.Add($line) }

    $seen = New-Object 'System.Collections.Generic.HashSet[string]'
    foreach ($source in $sources) {
        $dataStarted = $false
        foreach ($raw in [IO.File]::ReadLines($source, [Text.Encoding]::UTF8)) {
            if ($raw -eq "...") {
                $dataStarted = $true
                continue
            }
            if (-not $dataStarted -or -not $raw -or $raw.StartsWith("#") -or -not $raw.Contains("`t")) { continue }
            $parts = $raw -split "`t", 3
            if ($parts.Count -lt 2) { continue }
            $text = $parts[0].Trim()
            $code = $parts[1].Trim()
            if ($text.Length -ne 1) { continue }
            $codePoint = [int][char]$text[0]
            if ($codePoint -lt 0x3400 -or $codePoint -gt 0x9FFF) { continue }
            if ($code.Contains(",") -or $code.Contains(".")) { continue }
            $key = "$text`t$code"
            if (-not $seen.Add($key)) { continue }
            $normalizedCode = if ($code.StartsWith("~")) { $code.Substring(1) } else { $code }
            $weight = [Math]::Max(1, 10000 - $normalizedCode.Length * 100)
            [void]$lines.Add("$text`t$code`t$weight")
        }
    }
    Backup-File $Destination
    [IO.File]::WriteAllLines($Destination, $lines, $Utf8NoBom)
    return $true
}

function Find-WeaselDeployer {
    if ($DeployerPath) {
        if (Test-Path -LiteralPath $DeployerPath -PathType Leaf) {
            return (Resolve-Path -LiteralPath $DeployerPath).Path
        }
        throw "WeaselDeployer.exe was not found at: $DeployerPath"
    }

    $roots = New-Object 'System.Collections.Generic.List[string]'
    foreach ($registryPath in @(
        "HKLM:\SOFTWARE\Rime\Weasel",
        "HKLM:\SOFTWARE\WOW6432Node\Rime\Weasel"
    )) {
        try {
            $item = Get-ItemProperty -LiteralPath $registryPath -ErrorAction Stop
            foreach ($property in @("WeaselRoot", "InstallDir")) {
                $entry = $item.PSObject.Properties[$property]
                $value = if ($entry) { $entry.Value } else { $null }
                if ($value) { [void]$roots.Add([string]$value) }
            }
        }
        catch {
            # Continue with conventional install locations.
        }
    }
    foreach ($programRoot in @($env:ProgramFiles, ${env:ProgramFiles(x86)})) {
        if ($programRoot) { [void]$roots.Add((Join-Path $programRoot "Rime")) }
    }
    foreach ($rootPath in ($roots | Select-Object -Unique)) {
        $direct = Join-Path $rootPath "WeaselDeployer.exe"
        if (Test-Path -LiteralPath $direct -PathType Leaf) {
            return (Resolve-Path -LiteralPath $direct).Path
        }
        if (Test-Path -LiteralPath $rootPath -PathType Container) {
            $match = Get-ChildItem -LiteralPath $rootPath -Directory -Filter "weasel-*" -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending |
                ForEach-Object { Join-Path $_.FullName "WeaselDeployer.exe" } |
                Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
                Select-Object -First 1
            if ($match) { return (Resolve-Path -LiteralPath $match).Path }
        }
    }
    return $null
}

$TargetDir = Get-RimeUserDirectory
Write-Host "Xiapin Windows installer"
Write-Host "Rime user directory: $TargetDir"
if ($DryRun) {
    Write-Host "Dry-run mode: no files will be changed and Weasel will not be started."
}
else {
    New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $TargetDir "lua") | Out-Null
}

$files = @(
    @{ Source = "rime\xiapin.schema.yaml"; Destination = "xiapin.schema.yaml" },
    @{ Source = "rime\xiapin_english.schema.yaml"; Destination = "xiapin_english.schema.yaml" },
    @{ Source = "rime\xiapin.extended.dict.yaml"; Destination = "xiapin.extended.dict.yaml" },
    @{ Source = "rime\xiapin_custom.dict.yaml"; Destination = "xiapin_custom.dict.yaml" },
    @{ Source = "rime\xiapin_pinyin_liur.dict.yaml"; Destination = "xiapin_pinyin_liur.dict.yaml" },
    @{ Source = "rime\easy_en.dict.yaml"; Destination = "easy_en.dict.yaml" },
    @{ Source = "rime\xiapin_English.dict.yaml"; Destination = "xiapin_English.dict.yaml" },
    @{ Source = "rime\xiapin.custom.yaml"; Destination = "xiapin.custom.yaml" },
    @{ Source = "rime\lua\boshiamy_comment.lua"; Destination = "lua\boshiamy_comment.lua" }
)
foreach ($file in $files) { Copy-XiapinFile $file.Source $file.Destination }

Add-SchemasToDefaultCustom (Join-Path $TargetDir "default.custom.yaml")
$generated = Build-XiapinLiurDictionary (Join-Path $TargetDir "xiapin_liur.dict.yaml")
if ($generated -and -not $DryRun) {
    $extendedPath = Join-Path $TargetDir "xiapin.extended.dict.yaml"
    $extendedText = [IO.File]::ReadAllText($extendedPath, [Text.Encoding]::UTF8)
    if (-not $extendedText.Contains("- xiapin_liur")) {
        $extendedText = $extendedText.Replace("  - xiapin_pinyin_liur`n", "  - xiapin_pinyin_liur`n  - xiapin_liur`n")
        [IO.File]::WriteAllText($extendedPath, $extendedText, $Utf8NoBom)
    }
}

if ($DryRun) {
    Write-Host "Dry-run completed successfully."
    exit 0
}
if ($NoDeploy) {
    Write-Host "Files installed. Deployment was skipped because -NoDeploy was specified."
    exit 0
}

$deployer = Find-WeaselDeployer
if (-not $deployer) {
    Write-Warning "WeaselDeployer.exe was not found. Install Weasel, then choose Redeploy from its tray menu."
    Write-Host "Files are ready in: $TargetDir"
    exit 0
}

Write-Host "Deploying with: $deployer"
& $deployer /deploy
if ($LASTEXITCODE -ne 0) {
    throw "Weasel deployment failed with exit code $LASTEXITCODE."
}
Write-Host "Xiapin was installed and redeployed successfully."
Write-Host "Use Control+grave to choose Xiapin or Xiapin English."
