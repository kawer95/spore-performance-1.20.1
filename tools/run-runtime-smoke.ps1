param(
    [Parameter(Mandatory = $true)] [string]$VersionDirectory,
    [int]$TimeoutSeconds = 90,
    [int]$SmokePort = 25585,
    [string]$ForgeVersion = '1.20.1-47.4.22',
    [switch]$Aggressive,
    [switch]$ProbeCalamity,
    [switch]$DebugTrace,
    [ValidateSet('spore', 'sporefix', 'sporesrp', 'full')]
    [string]$ModuleSet = 'full'
)

$ErrorActionPreference = 'Stop'
$script:utf8NoBom = New-Object System.Text.UTF8Encoding($false)
function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory = $true)] [string]$Path,
        [AllowNull()] [string]$Value
    )
    [System.IO.File]::WriteAllText($Path, $Value, $script:utf8NoBom)
}
$project = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $project 'run\runtime-smoke'
$mods = Join-Path $runtime 'mods'
$logs = Join-Path $runtime 'logs'
New-Item -ItemType Directory -Force -Path $runtime, $mods, $logs | Out-Null

$runBat = Join-Path $runtime 'run.bat'
if (-not (Test-Path -LiteralPath $runBat)) {
    $installer = Join-Path $runtime "forge-$ForgeVersion-installer.jar"
    if (-not (Test-Path -LiteralPath $installer)) {
        $uri = "https://maven.minecraftforge.net/net/minecraftforge/forge/$ForgeVersion/forge-$ForgeVersion-installer.jar"
        Invoke-WebRequest -Uri $uri -OutFile $installer
    }
    Push-Location $runtime
    try {
        & java -jar $installer --installServer
        if ($LASTEXITCODE -ne 0) { throw "Forge server installer exited with code $LASTEXITCODE" }
    } finally { Pop-Location }
    if (-not (Test-Path -LiteralPath $runBat)) { throw 'Forge installer did not create run.bat.' }
}

$sourceMods = Join-Path $VersionDirectory 'mods'
$spore = Get-ChildItem -LiteralPath $sourceMods -Filter '*spore_1.20.1_2.2.0j.jar' | Select-Object -First 1
$aiFix = Get-ChildItem -LiteralPath $sourceMods -Filter '*exhuashan_sporeai_fix-1.0.0.jar' | Select-Object -First 1
$sporeSrp = Get-ChildItem -LiteralPath $sourceMods | Where-Object { $_.Name -like '*sporesrp-1.7.2.jar' } | Select-Object -First 1
$tacz = Get-ChildItem -LiteralPath $sourceMods | Where-Object { $_.Name -like '*tacz-1.20.1-1.1.8-hotfix.jar' } | Select-Object -First 1
$addon = Get-ChildItem -LiteralPath (Join-Path $project 'build\libs') -Filter 'spore_performance-*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$required = @($spore, $addon)
if ($ModuleSet -eq 'sporefix' -or $ModuleSet -eq 'full') { $required += $aiFix }
if ($ModuleSet -eq 'sporesrp' -or $ModuleSet -eq 'full') { $required += $sporeSrp }
if ($required | Where-Object { $null -eq $_ }) { throw 'One or more required smoke-test JARs are missing. Build the add-on before running this script.' }
# The isolated server keeps no other test mods; remove only the four JAR naming families that
# this script owns so the requested optional-dependency matrix cannot inherit a stale JAR.
foreach ($pattern in @('*spore_1.20.1_2.2.0j.jar', '*exhuashan_sporeai_fix-1.0.0.jar', '*sporesrp-1.7.2.jar', 'spore_performance-*.jar')) {
    Get-ChildItem -LiteralPath $mods -Filter $pattern -ErrorAction SilentlyContinue | Remove-Item -Force
}
foreach ($jar in $required) { Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $mods $jar.Name) -Force }
if ($null -ne $tacz) {
    Get-ChildItem -LiteralPath $mods -Filter '*tacz-1.20.1-*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force
    Copy-Item -LiteralPath $tacz.FullName -Destination (Join-Path $mods $tacz.Name) -Force
}

Set-Content -LiteralPath (Join-Path $runtime 'eula.txt') -Value 'eula=true' -NoNewline -Encoding Ascii
$serverProperties = Join-Path $runtime 'server.properties'
$smokeLevelName = "world-smoke-$ModuleSet"
if (Test-Path -LiteralPath $serverProperties) {
    $propertiesText = Get-Content -LiteralPath $serverProperties -Raw
    $propertiesText = $propertiesText -replace '(?m)^server-port=\d+$', "server-port=$SmokePort"
    $propertiesText = $propertiesText -replace '(?m)^query.port=\d+$', "query.port=$SmokePort"
    $propertiesText = $propertiesText -replace '(?m)^level-name=.*$', "level-name=$smokeLevelName"
    Write-Utf8NoBom -Path $serverProperties -Value $propertiesText
}
$commonConfig = Join-Path $runtime 'config\spore_performance-common.toml'
$commonTemplate = Join-Path $project 'src\main\resources\defaultconfigs\spore_performance-common.toml'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $commonConfig) | Out-Null
Copy-Item -LiteralPath $commonTemplate -Destination $commonConfig -Force
if ($Aggressive) {
    $configText = Get-Content -LiteralPath $commonConfig -Raw
    foreach ($key in @(
        'moundTendrilScheduler', 'foliageScheduler', 'calamityPathBackoff', 'remoteIdleAi',
        'howitzerTrajectoryCache', 'groupSensingCache', 'sporesrpLazyFullHivemindQueue',
        'sporesrpMiningBudget', 'sporesrpSurfaceSearchScheduler', 'sporesrpCasingScheduler',
        'sporefixPermanentAuditScheduler', 'foliageFastCursor', 'foliageDirectLoadedChunkRead',
        'foliageTimeBudget', 'tendrilTimeBudget', 'followPathReuse', 'followPathFailureBackoff'
    )) {
        $configText = $configText -replace "(?m)^$key = false$", "$key = true"
    }
    $configText = $configText -replace '(?m)^sporesrpProtoStagger = 1$', 'sporesrpProtoStagger = 2'
    $configText = $configText -replace '(?m)^sporesrpFullHivemindStagger = 1$', 'sporesrpFullHivemindStagger = 2'
    $configText = $configText -replace '(?m)^sporesrpBuilderStagger = 1$', 'sporesrpBuilderStagger = 2'
    $configText = $configText -replace '(?m)^metrics = false$', 'metrics = true'
    Write-Utf8NoBom -Path $commonConfig -Value $configText
}
if ($DebugTrace) {
    $configText = Get-Content -LiteralPath $commonConfig -Raw
    $configText = $configText -replace '(?ms)(\[diagnostics\.debugTrace\].*?^\s*enabled\s*=\s*)false', '${1}true'
    Write-Utf8NoBom -Path $commonConfig -Value $configText
    Remove-Item -LiteralPath (Join-Path $logs 'spore-performance-debug.jsonl') -Force -ErrorAction SilentlyContinue
}

# This datapack is owned by the isolated smoke world.  It forces the lazy
# entity/goal classes behind every server-side Mixin target to transform and
# construct before the server reports ready.  In particular, it covers the
# CalamityPathNavigation constructor path that previously threw
# IllegalClassLoadError only after a Stahlmorder was created in-game.
$probeRoot = Join-Path $runtime "$smokeLevelName\datapacks\spore-performance-calamity-probe"
if (Test-Path -LiteralPath $probeRoot) { Remove-Item -LiteralPath $probeRoot -Recurse -Force }
if ($ProbeCalamity) {
    $loadTag = Join-Path $probeRoot 'data\minecraft\tags\functions'
    $functions = Join-Path $probeRoot 'data\spore_performance_smoke\functions'
    New-Item -ItemType Directory -Force -Path $loadTag, $functions | Out-Null
    Write-Utf8NoBom -Path (Join-Path $probeRoot 'pack.mcmeta') -Value @'
{
  "pack": { "pack_format": 15, "description": "Spore Performance isolated runtime probe" }
}
'@
    Write-Utf8NoBom -Path (Join-Path $loadTag 'load.json') -Value @'
{ "values": ["spore_performance_smoke:load"] }
'@
    Write-Utf8NoBom -Path (Join-Path $functions 'load.mcfunction') -Value @'
summon spore:stahl 0 80 0
summon spore:gazenbreacher -24 80 0
summon spore:kraken -16 80 0
summon spore:hindenburg -8 84 0
summon spore:leviathan 0 80 16
summon spore:sieger 8 80 16
summon spore:verfall 16 84 16
summon spore_performance:stahl_rising_block 2 80 0
summon spore:howitzer 8 80 0
summon spore:inf_human 16 80 0
summon spore:tentacle 24 80 0
summon spore:mound 32 80 0
summon spore:gastgaber 36 80 0
summon spore:tendril 38 80 0
summon spore:bile 40 82 0 {Motion:[0.1d,0.0d,0.0d]}
summon spore:hohlfresser 44 80 0
summon spore:grakensenker 48 80 0
summon spore:grober 52 80 0
summon spore:howler 56 80 0
summon spore:scamper 60 80 0
summon spore:braurei 64 80 0
summon spore:vigil 68 80 0
summon spore:tumoroid_nuke 72 80 0
summon item 48 80 0 {Item:{id:"spore:biomass",Count:32b}}
summon item 49 80 0 {Item:{id:"spore:biomass",Count:32b}}
setblock 40 80 0 spore:overgrown_spawner
setblock 42 80 0 spore:incubator
setblock 44 80 2 spore:cdu
'@
}
$latestLog = Join-Path $logs 'latest.log'
$stdout = Join-Path $logs 'smoke-stdout.log'
$stderr = Join-Path $logs 'smoke-stderr.log'
Remove-Item -LiteralPath $latestLog, $stdout, $stderr -Force -ErrorAction SilentlyContinue
$process = Start-Process -FilePath $env:ComSpec -ArgumentList @('/c', 'run.bat nogui') -WorkingDirectory $runtime -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$ready = $false
try {
    while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
        Start-Sleep -Seconds 1
        $text = if (Test-Path -LiteralPath $latestLog) { Get-Content -LiteralPath $latestLog -Raw } else { '' }
        if ($text -match 'Done \([\d.]+s\)! For help, type "help"') { $ready = $true; break }
        if ($text -match 'IllegalClassLoadError|Mixin apply failed|InvalidInjectionException|MixinTransformerError|NoSuchMethodError|NoClassDefFoundError|LinkageError|VerifyError') {
            throw 'Runtime smoke test encountered a Mixin or linkage failure.'
        }
    }
    if (-not $ready) { throw "Runtime smoke test did not reach server-ready state within $TimeoutSeconds seconds. See $latestLog, $stdout, and $stderr" }
    if ($ProbeCalamity) {
        # Load-tag functions run immediately after the server reaches readiness.
        # Keep it alive long enough to complete every forced construction, then
        # inspect the log once more instead of racing the server shutdown.
        Start-Sleep -Seconds 2
        $probeLog = if (Test-Path -LiteralPath $latestLog) { Get-Content -LiteralPath $latestLog -Raw } else { '' }
        if ($probeLog -match 'IllegalClassLoadError|Mixin apply failed|InvalidInjectionException|MixinTransformerError|NoSuchMethodError|NoClassDefFoundError|LinkageError|VerifyError|Unknown entity|Failed to execute function') {
            throw 'Calamity probe encountered a transformation, linkage, or command failure.'
        }
    }
    if ($DebugTrace) {
        Start-Sleep -Seconds 2
        $debugLog = Join-Path $logs 'spore-performance-debug.jsonl'
        if (-not (Test-Path -LiteralPath $debugLog) -or (Get-Item -LiteralPath $debugLog).Length -eq 0) {
            throw 'Debug trace was enabled but produced no structured log.'
        }
        Get-Content -LiteralPath $debugLog | Select-Object -First 20 | ForEach-Object { $_ | ConvertFrom-Json | Out-Null }
    }
} finally {
    Get-CimInstance Win32_Process -Filter "ParentProcessId = $($process.Id)" -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
}
Write-Output "Runtime smoke test passed ($ModuleSet, aggressive=$Aggressive, calamityProbe=$ProbeCalamity, debugTrace=$DebugTrace). Logs: $logs"
