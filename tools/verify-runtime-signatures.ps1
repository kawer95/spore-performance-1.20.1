param(
    [string]$ModsDirectory = 'E:\斗蛐蛐\.minecraft\versions\国潮红师2\mods'
)

$ErrorActionPreference = 'Stop'
$spore = Get-ChildItem -LiteralPath $ModsDirectory -Filter '*spore_1.20.1_2.2.0j.jar' | Select-Object -First 1
$aiFix = Get-ChildItem -LiteralPath $ModsDirectory -Filter '*exhuashan_sporeai_fix-1.0.0.jar' | Select-Object -First 1
$sporesrp = Get-ChildItem -LiteralPath $ModsDirectory -Filter 'sporesrp-1.7.2.jar' | Select-Object -First 1
$embeddium = Get-ChildItem -LiteralPath $ModsDirectory -Filter '*embeddium-0.3.31+mc1.20.1.jar' | Select-Object -First 1
$acceleratedRendering = Get-ChildItem -LiteralPath $ModsDirectory -Filter '*acceleratedrendering-1.0.14-1.20.1-alpha.jar' | Select-Object -First 1
if ($null -eq $spore -or $null -eq $aiFix -or $null -eq $sporesrp -or $null -eq $embeddium -or $null -eq $acceleratedRendering) { throw 'The expected Spore, AI Fix, sporesrp, Embeddium, and AcceleratedRendering runtime JARs were not all found.' }

$checks = @(
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.AI.CalamityPathNavigation'; Member = 'm_26577_' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.AI.CalamityPathNavigation'; Member = 'm_6570_' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.AI.CalamityPathNavigation'; Member = 'm_5624_' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.AI.HybridPathNavigation'; Member = 'm_6570_' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.AI.HurtTargetGoal'; Member = 'alertOthers' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.AI.AOEMeleeAttackGoal'; Member = 'checkAndPerformAttack' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.AI.LocHiv.LocalTargettingGoal'; Member = 'Targeting' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.AI.LocHiv.FollowOthersGoal'; Member = 'findNearestPartner' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.AI.CalamitiesAI.CalamityInfectedCommand'; Member = 'Targeting' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.AI.LocHiv.SearchAreaGoal'; Member = 'm_8045_' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.BaseEntities.Hyper$GoBackToTheNest'; Member = 'm_8036_' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.Calamities.Hinderburg'; Member = 'm_8119_' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.Calamities.Grakensenker'; Member = 'applyVortexForces' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.Calamities.Stahlmorder$StaLeapGoal'; Member = 'm_8036_' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.Calamities.Stahlmorder$StaLeapGoal'; Member = 'm_8056_' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.Calamities.Stahlmorder'; Member = 'decideAnimation' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.Calamities.Stahlmorder'; Member = 'applyAttackEffect' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.Calamities.Stahlmorder$StahlMeleeAttackGoal'; Member = 'startDelayedAttack' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.Calamities.Stahlmorder$StahlMeleeAttackGoal'; Member = 'checkAndPerformAttack' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.AI.HybridPathNavigation$SwimmingNode'; Member = 'm_8086_' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.AI.CalamityPathNavigation$WaterCalamityNodeEvaluator'; Member = 'm_8086_' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Sentities.Organoids.Mound'; Member = 'SpreadKin' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.SBlockEntities.OvergrownSpawnerEntity'; Member = 'feed' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Client.Special.BaseInfectedRenderer'; Member = 'm_7392_' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Client.Special.BaseInfectedRenderer'; Member = 'getForm' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Client.Layers.EyeLayer'; Member = 'm_6494_' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Client.Layers.TranslucentLayer'; Member = 'render' },
    @{ Jar = $spore.FullName; Class = 'com.Harbinger.Spore.Client.Layers.HowitzerLightsLayer'; Member = 'renderActiveLight' },
    @{ Jar = $sporesrp.FullName; Class = 'com.maha_fish.sporesrp.handler.ProtoSkillsHandler'; Member = 'onServerTick' },
    @{ Jar = $sporesrp.FullName; Class = 'com.maha_fish.sporesrp.handler.ProtoSkillsHandler'; Member = 'scanForSurface' },
    @{ Jar = $sporesrp.FullName; Class = 'com.maha_fish.sporesrp.handler.FullHivemindSkillsHandler'; Member = 'scanForSurface' },
    @{ Jar = $sporesrp.FullName; Class = 'com.maha_fish.sporesrp.handler.FullHivemindHandler'; Member = 'generateSphereQueue' },
    @{ Jar = $sporesrp.FullName; Class = 'com.maha_fish.sporesrp.handler.FullHivemindHandler'; Member = 'buildCasings' },
    @{ Jar = $sporesrp.FullName; Class = 'com.maha_fish.sporesrp.handler.GastgaberBuilderHandler'; Member = 'onServerTick' },
    @{ Jar = $sporesrp.FullName; Class = 'com.maha_fish.sporesrp.client.HUDOverlay'; Member = 'onRenderGui' },
    @{ Jar = $embeddium.FullName; Class = 'me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer'; Member = 'renderModel' },
    @{ Jar = $embeddium.FullName; Class = 'me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext'; Member = 'state' },
    @{ Jar = $acceleratedRendering.FullName; Class = 'com.github.argon4w.acceleratedrendering.core.CoreFeature'; Member = 'isLoaded' },
    @{ Jar = $acceleratedRendering.FullName; Class = 'com.github.argon4w.acceleratedrendering.core.CoreFeature'; Member = 'forceEnableForceTranslucentAcceleration' },
    @{ Jar = $acceleratedRendering.FullName; Class = 'com.github.argon4w.acceleratedrendering.core.CoreFeature'; Member = 'resetForceTranslucentAcceleration' }
)

foreach ($check in $checks) {
    $output = & javap -classpath $check.Jar -p $check.Class 2>&1
    if ($LASTEXITCODE -ne 0 -or -not ($output -match [regex]::Escape($check.Member))) { throw "Missing runtime signature $($check.Class)::$($check.Member)" }
    Write-Output "OK $($check.Class)::$($check.Member)"
}
Write-Output 'Runtime signature matrix passed.'
