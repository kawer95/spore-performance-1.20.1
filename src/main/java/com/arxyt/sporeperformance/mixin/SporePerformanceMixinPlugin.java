package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.compat.MixinPatchStatus;
import com.arxyt.sporeperformance.compat.MixinBytecodeContract;
import com.arxyt.sporeperformance.compat.OptionalCompatProbe;
import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Keeps optional target classes out of the transformation set when their owning mod is absent.
 * Runtime probes perform stricter method checks after Forge has completed class transformation.
 */
public final class SporePerformanceMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> AI_FIX = Set.of("OptionalHowitzerMixin", "OptionalStormFortressClientMixin", "OptionalImmortalAuditMixin");
    private static final Set<String> SPORESRP = Set.of("OptionalSporeSrpProtoSkillsMixin", "OptionalSporeSrpMarkedMoundMixin", "OptionalSporeSrpFullHivemindMixin", "OptionalSporeSrpBuilderMixin", "OptionalSporeSrpFullHivemindMiningMixin", "OptionalSporeSrpHudMixin", "OptionalSporeSrpDamageBypassMixin");
    private static final Set<String> EMBEDDIUM = Set.of("OptionalEmbeddiumBlockRendererMixin");
    private static final Set<String> SONA = Set.of(
            "OptionalSonaCanChunkMixin", "OptionalSonaInfectionShaderPostMixin", "OptionalSonaInfectionOverlayMixin");
    private static final Set<String> TOUHOU = Set.of("OptionalTouhouPowerPointMixin");
    private static final Set<String> TACZ = Set.of("CalamityDamageBypassMixin", "SporeAdaptationBypassMixin");
    private static final Set<String> SPORE_RENDER = Set.of(
            "BaseInfectedRendererMixin", "TranslucentLayerAnimationMixin", "DrakeMembraneLayerAnimationMixin",
            "BreweryLiquidAnimationMixin", "EyeLayerRenderMixin", "BairnEyeLayerRenderMixin",
            "TranslucentLayerRenderMixin", "DrakeMembraneLayerRenderMixin", "CalamityVeinsRenderMixin",
            "WaterCalamityCamoRenderMixin", "BreweryLiquidRenderMixin", "HowitzerEmissiveLayerRenderMixin",
            "HowitzerLightsLayerRenderMixin", "HindenburgLightsLayerRenderMixin");
    private static final Set<String> SPORE_AI_REFACTOR = Set.of(
            "HurtTargetGoalMixin", "AOEMeleeAttackGoalMixin", "CustomMeleeAttackGoalMetricsMixin",
            "LocalTargettingGoalMixin", "FollowOthersGoalMixin", "CalamityPathNavigationMixin",
            "CalamitySmoothLookControlMixin", "CalamityMovementControlMixin", "UndergroundMovementControlMixin",
            "HindenburgLookControlMixin", "HybridPathNavigationMixin", "UndergroundPathNavigationMixin",
            "HohlfresserMultipartSafetyMixin", "BusserAirNavigationMixin", "BusserVariantMixin");
    private static final Set<String> SPORE_BILE_LIFETIME = Set.of("BileProjectileLifetimeMixin");
    private static final Set<String> PORTED_AI_FIX_ABSENT_ONLY = Set.of(
            "AiFixSwimmingNodeMixin", "AiFixWaterCalamityNodeMixin");
    private static final Set<String> DELEGATED_STAHL = Set.of(
            "AiFixStahlLeapGuardMixin", "AiFixStahlMeleeGoalMixin", "AiFixStahlmorderControlMixin");
    private static final String STAHL_CLASS = "com.Harbinger.Spore.Sentities.Calamities.Stahlmorder";
    private static final Set<String> STAHL_CONTROL_METHODS = Set.of(
            "m_8119_", "m_7350_", "m_7327_", "m_7822_", "createAttributes",
            "decideAnimation", "applyAttackEffect");
    /**
     * These methods are already transformed by the installed AI Fix.  Applying a second
     * redirect at the same priority is not composable in Mixin and aborts class loading, so the
     * optional dependency owns this narrow implementation detail while our higher-level
     * navigation runtime remains active.
     */
    private static final Set<String> AI_FIX_OWNED_CONTROLLERS = Set.of("UndergroundMovementControlMixin");
    private static final Map<String, Set<String>> EXPECTED_METHODS = Map.ofEntries(
            Map.entry("OptionalHowitzerMixin", Set.of("m_142582_|hasLineOfSight")),
            Map.entry("OptionalStormFortressClientMixin", Set.of("spawnPersistentAura", "renderStars")),
            Map.entry("OptionalImmortalAuditMixin", Set.of("auditProtectedEntities")),
            Map.entry("OptionalSporeSrpProtoSkillsMixin", Set.of("onServerTick", "scanForSurface")),
            Map.entry("OptionalSporeSrpMarkedMoundMixin", Set.of("onServerTick")),
            Map.entry("OptionalSporeSrpFullHivemindMixin", Set.of("onServerTick", "scanForSurface")),
            Map.entry("OptionalSporeSrpBuilderMixin", Set.of("onServerTick")),
            Map.entry("OptionalSporeSrpFullHivemindMiningMixin", Set.of("processMining", "generateSphereQueue", "buildCasings", "buildCasingsOnce")),
            Map.entry("OptionalSporeSrpHudMixin", Set.of("onRenderGui")),
            Map.entry("OptionalEmbeddiumBlockRendererMixin", Set.of("renderModel")),
            Map.entry("OptionalSonaInfectionShaderPostMixin", Set.of("copyMainTarget", "renderShaderPost")),
            Map.entry("OptionalSonaInfectionOverlayMixin", Set.of("onRenderInfectionSpores", "renderSporeParticles")),
            Map.entry("OptionalSonaCanChunkMixin", Set.of("canChunkInfection")),
            Map.entry("OptionalTouhouPowerPointMixin", Set.of("m_8119_", "followingMovement")),
            Map.entry("BaseInfectedRendererMixin", Set.of("render|m_7392_", "getForm")),
            Map.entry("TranslucentLayerAnimationMixin", Set.of("render")),
            Map.entry("DrakeMembraneLayerAnimationMixin", Set.of("render")),
            Map.entry("BreweryLiquidAnimationMixin", Set.of("render")),
            Map.entry("EyeLayerRenderMixin", Set.of("render|m_6494_")),
            Map.entry("BairnEyeLayerRenderMixin", Set.of("render")),
            Map.entry("TranslucentLayerRenderMixin", Set.of("render")),
            Map.entry("DrakeMembraneLayerRenderMixin", Set.of("render")),
            Map.entry("CalamityVeinsRenderMixin", Set.of("render")),
            Map.entry("WaterCalamityCamoRenderMixin", Set.of("render")),
            Map.entry("BreweryLiquidRenderMixin", Set.of("render")),
            Map.entry("HowitzerEmissiveLayerRenderMixin", Set.of("render")),
            Map.entry("HowitzerLightsLayerRenderMixin", Set.of("render", "renderActiveLight")),
            Map.entry("HindenburgLightsLayerRenderMixin", Set.of("render", "renderActiveLight"))
            ,Map.entry("CalamityDamageBypassMixin", Set.of("m_6469_"))
            ,Map.entry("SporeAdaptationBypassMixin", Set.of("m_6469_"))
            ,Map.entry("OptionalSporeSrpDamageBypassMixin", Set.of("onLivingHurt"))
            ,Map.entry("HurtTargetGoalMixin", Set.of("alertOthers"))
            ,Map.entry("AOEMeleeAttackGoalMixin", Set.of("checkAndPerformAttack"))
            ,Map.entry("CustomMeleeAttackGoalMetricsMixin", Set.of("m_8036_", "m_8056_", "m_8041_", "m_8037_"))
            ,Map.entry("LocalTargettingGoalMixin", Set.of("Targeting"))
            ,Map.entry("FollowOthersGoalMixin", Set.of("m_8036_", "findNearestPartner"))
            ,Map.entry("CalamityPathNavigationMixin", Set.of("m_7864_", "m_6570_", "m_5624_", "m_7638_", "m_26577_"))
            ,Map.entry("CalamitySmoothLookControlMixin", Set.of("m_8128_"))
            ,Map.entry("CalamityMovementControlMixin", Set.of("m_8126_"))
            ,Map.entry("UndergroundMovementControlMixin", Set.of("moveUnderground"))
            ,Map.entry("HindenburgLookControlMixin", Set.of("m_8128_"))
            ,Map.entry("HybridPathNavigationMixin", Set.of("m_7864_", "m_6570_", "m_5624_", "m_7638_", "m_26577_"))
            ,Map.entry("UndergroundPathNavigationMixin", Set.of("m_7864_", "m_6570_", "m_5624_", "m_7638_"))
            ,Map.entry("HohlfresserMultipartSafetyMixin", Set.of("m_8119_", "m_142687_", "parts", "summonCorpsePart"))
            ,Map.entry("AiFixCalamityCommandMixin", Set.of("Targeting"))
            ,Map.entry("AiFixSearchAreaGoalMixin", Set.of("m_8045_", "m_8037_"))
            ,Map.entry("AiFixInfectedSearchPersistenceMixin", Set.of("m_7380_", "m_7378_"))
            ,Map.entry("AiFixCalamityGoalGuardsMixin", Set.of("m_8036_", "m_8045_"))
            ,Map.entry("AiFixLeapGoalMixin", Set.of("m_8036_", "m_8045_"))
            ,Map.entry("AiFixHyperNestGoalMixin", Set.of("m_8036_", "m_8045_"))
            ,Map.entry("AiFixHyperRandomStrollMixin", Set.of("canUse|m_8036_"))
            ,Map.entry("AiFixSwimmingNodeMixin", Set.of("m_8086_"))
            ,Map.entry("AiFixWaterCalamityNodeMixin", Set.of("m_8086_"))
            ,Map.entry("AiFixHinderburgTargetMixin", Set.of("m_8119_"))
            ,Map.entry("AiFixGrakensenkerWorkMixin", Set.of("applyVortexForces"))
            ,Map.entry("AiFixStahlLeapGuardMixin", Set.of("m_8036_", "m_8056_"))
            ,Map.entry("AiFixStahlmorderControlMixin", Set.of("m_8119_", "m_7350_", "m_7327_", "m_7822_", "createAttributes", "decideAnimation", "applyAttackEffect"))
            ,Map.entry("AiFixStahlMeleeGoalMixin", Set.of("m_8045_", "m_8037_", "checkAndPerformAttack", "resetAttackCooldown", "startDelayedAttack"))
            ,Map.entry("BileProjectileLifetimeMixin", Set.of("m_8119_"))
            ,Map.entry("BusserAirNavigationMixin", Set.of("tryShortcut", "sweep"))
            ,Map.entry("BusserVariantMixin", Set.of("m_7350_", "addVariantGoals"))
            ,Map.entry("HyperEntityDataOwnerMixin", Set.of("<clinit>"))
            ,Map.entry("HowlerEntityDataOwnerMixin", Set.of("<clinit>"))
            ,Map.entry("ScamperEntityDataOwnerMixin", Set.of("<clinit>"))
            ,Map.entry("BrauereiEntityDataOwnerMixin", Set.of("<clinit>"))
            ,Map.entry("VigilEntityDataOwnerMixin", Set.of("<clinit>"))
            ,Map.entry("TumoroidNukeEntityDataOwnerMixin", Set.of("<clinit>"))
            ,Map.entry("IncubatorBlockEntitySyncMixin", Set.of("setFuel", "m_6836_", "m_7407_", "m_6211_"))
            ,Map.entry("CduBlockEntitySyncMixin", Set.of("setFuel", "serverTick"))
            ,Map.entry("OvergrownSpawnerEntityMixin", Set.of("feed"))
    );
    private static final Map<String, Set<String>> EXPECTED_SIGNATURES = Map.of(
            "OptionalSonaCanChunkMixin", Set.of(
                    "canChunkInfection(Lnet/minecraft/world/level/Level;)Z"),
            "OptionalSonaInfectionOverlayMixin", Set.of(
                    "onRenderInfectionSpores(Lnet/minecraftforge/client/event/RenderGuiEvent$Pre;)V",
                    "renderSporeParticles(Lnet/minecraftforge/client/event/RenderGuiEvent$Pre;Lnet/minecraft/world/phys/Vec3;IIFF)V"),
            "OptionalSonaInfectionShaderPostMixin", Set.of(
                    "onRenderShaderPost(Lnet/minecraftforge/client/event/RenderGuiEvent$Pre;)V",
                    "copyMainTarget(Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/mojang/blaze3d/pipeline/TextureTarget;)V",
                    "renderShaderPost(Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/mojang/blaze3d/pipeline/TextureTarget;Lnet/minecraft/client/renderer/ShaderInstance;Lnet/minecraft/world/phys/Vec3;FFF)V")
    );

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        // Harium already owns ProjectileUtil's broad-phase redirect. Applying both redirects at
        // priority 1000 leaves the losing mixin with zero matches and crashes on first projectile.
        if (simple.equals("ProjectileUtilMixin")
                && FMLLoader.getLoadingModList().getModFileById("harium") != null) {
            MixinPatchStatus.record(simple, OptionalCompatProbe.State.SKIPPED);
            return false;
        }
        if (AI_FIX.contains(simple)) {
            if (FMLLoader.getLoadingModList().getModFileById("exhuashan_sporeai_fix") == null) {
                MixinPatchStatus.record(simple, OptionalCompatProbe.State.SKIPPED);
                return false;
            }
            return recordCompatibility(simple, targetClassName);
        }
        if (SPORESRP.contains(simple)) {
            if (FMLLoader.getLoadingModList().getModFileById("sporesrp") == null) {
                MixinPatchStatus.record(simple, OptionalCompatProbe.State.SKIPPED);
                return false;
            }
            return recordCompatibility(simple, targetClassName);
        }
        if (EMBEDDIUM.contains(simple)) {
            if (FMLLoader.getLoadingModList().getModFileById("embeddium") == null) {
                MixinPatchStatus.record(simple, OptionalCompatProbe.State.SKIPPED);
                return false;
            }
            return recordCompatibility(simple, targetClassName);
        }
        if (SONA.contains(simple)) {
            if (FMLLoader.getLoadingModList().getModFileById("sona") == null) {
                MixinPatchStatus.record(simple, OptionalCompatProbe.State.SKIPPED);
                return false;
            }
            return recordCompatibility(simple, targetClassName);
        }
        if (TOUHOU.contains(simple)) {
            if (FMLLoader.getLoadingModList().getModFileById("touhoulittlemaid") == null) {
                MixinPatchStatus.record(simple, OptionalCompatProbe.State.SKIPPED);
                return false;
            }
            return recordCompatibility(simple, targetClassName);
        }
        if (TACZ.contains(simple)) {
            if (FMLLoader.getLoadingModList().getModFileById("tacz") == null) {
                MixinPatchStatus.record(simple, OptionalCompatProbe.State.SKIPPED);
                return false;
            }
            return recordCompatibility(simple, targetClassName);
        }
        if (AI_FIX_OWNED_CONTROLLERS.contains(simple)
                && FMLLoader.getLoadingModList().getModFileById("exhuashan_sporeai_fix") != null) {
            MixinPatchStatus.record(simple, OptionalCompatProbe.State.SKIPPED);
            return false;
        }
        if (SPORE_RENDER.contains(simple)) return recordCompatibility(simple, targetClassName);
        if (SPORE_AI_REFACTOR.contains(simple)) return recordCompatibility(simple, targetClassName);
        if (SPORE_BILE_LIFETIME.contains(simple)) return recordCompatibility(simple, targetClassName);
        if (simple.startsWith("AiFix")) {
            // AI Fix 1.0.0 already owns this exact SearchAreaGoal state-machine and 9 -> 3
            // arrival-radius change. Skipping our duplicate avoids a Mixin warning without
            // changing its behavior.
            if (simple.equals("AiFixSearchAreaGoalMixin")
                    && FMLLoader.getLoadingModList().getModFileById("exhuashan_sporeai_fix") != null) {
                MixinPatchStatus.record(simple, OptionalCompatProbe.State.SKIPPED);
                return false;
            }
            // AI Fix owns Stahl's combat state machine when installed.  These
            // mixins are ports for a Spore-only stack; applying both versions
            // would make overwrite priority decide which landing damage,
            // values and block effects exist at runtime.
            if (DELEGATED_STAHL.contains(simple)
                    && FMLLoader.getLoadingModList().getModFileById("exhuashan_sporeai_fix") != null) {
                MixinPatchStatus.record(simple, OptionalCompatProbe.State.SKIPPED);
                return false;
            }
            // The leap and melee hooks call methods supplied by the outer
            // Stahl control mixin.  Probe that complete transform first so a
            // partial application can never leave an unsafe cast behind.
            if (DELEGATED_STAHL.contains(simple) && !stahlControlTransformCompatible()) {
                MixinPatchStatus.record(simple, OptionalCompatProbe.State.INCOMPATIBLE);
                return false;
            }
            if (PORTED_AI_FIX_ABSENT_ONLY.contains(simple)
                    && FMLLoader.getLoadingModList().getModFileById("exhuashan_sporeai_fix") != null) {
                MixinPatchStatus.record(simple, OptionalCompatProbe.State.SKIPPED);
                return false;
            }
            return recordCompatibility(simple, targetClassName);
        }
        return recordCompatibility(simple, targetClassName);
    }

    private static boolean stahlControlTransformCompatible() {
        if (!classPresent(STAHL_CLASS)
                || !hasExpectedMethods(STAHL_CLASS, STAHL_CONTROL_METHODS, Set.of())) return false;
        return MixinBytecodeContract.verify(STAHL_CLASS,
                "com.arxyt.sporeperformance.mixin.AiFixStahlmorderControlMixin").compatible();
    }

    private static boolean recordCompatibility(String mixin, String target) {
        boolean basicCompatible = classPresent(target) && hasExpectedMethods(
                target,
                EXPECTED_METHODS.getOrDefault(mixin, Set.of()),
                EXPECTED_SIGNATURES.getOrDefault(mixin, Set.of()));
        boolean directTransformCompatible = basicCompatible && MixinBytecodeContract.verify(target,
                "com.arxyt.sporeperformance.mixin." + mixin).compatible();
        OptionalCompatProbe.State result = directTransformCompatible
                ? OptionalCompatProbe.State.ACTIVE : OptionalCompatProbe.State.INCOMPATIBLE;
        MixinPatchStatus.record(mixin, result);
        return result == OptionalCompatProbe.State.ACTIVE;
    }

    private static boolean classPresent(String binaryName) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null && loader.getResource(binaryName.replace('.', '/') + ".class") != null;
    }

    private static boolean hasExpectedMethods(String binaryName, Set<String> expectedNames, Set<String> expectedSignatures) {
        if (expectedNames.isEmpty() && expectedSignatures.isEmpty()) return true;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) return false;
        String resource = binaryName.replace('.', '/') + ".class";
        try (InputStream input = loader.getResourceAsStream(resource)) {
            if (input == null) return false;
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            Set<String> foundNames = new java.util.HashSet<>();
            Set<String> foundSignatures = new java.util.HashSet<>();
            node.methods.forEach(method -> {
                foundNames.add(method.name);
                foundSignatures.add(method.name + method.desc);
            });
            return expectedNames.stream().allMatch(group -> java.util.Arrays.stream(group.split("\\|"))
                    .anyMatch(foundNames::contains)) && foundSignatures.containsAll(expectedSignatures);
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
