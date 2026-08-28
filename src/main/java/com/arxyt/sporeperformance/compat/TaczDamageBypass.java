package com.arxyt.sporeperformance.compat;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Server-side, fail-closed TACZ compatibility for the two independent Spore/SRP
 * incoming-damage protections.  The hot path only performs an instanceof check,
 * one cached MethodHandle call and two immutable-set lookups; it never searches
 * classes or reads Forge ConfigValue objects.
 */
@Mod.EventBusSubscriber(modid = SporePerformance.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TaczDamageBypass {
    private static final String TACZ_BULLET_CLASS = "com.tacz.guns.entity.EntityKineticBullet";
    private static final ThreadLocal<ArrayDeque<Context>> CALAMITY_CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static volatile Snapshot snapshot = Snapshot.disabled("not initialized");
    private static volatile Class<?> kineticBulletClass;
    private static volatile MethodHandle gunIdHandle;

    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == PerformanceConfig.COMMON_SPEC) refresh();
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == PerformanceConfig.COMMON_SPEC) refresh();
    }

    /** Refreshes all immutable state after Forge has bound the common config. */
    public static void refresh() {
        final boolean autoDetect;
        final boolean bypassCap;
        final boolean bypassAdaptation;
        final List<? extends String> capIds;
        final List<? extends String> adaptationIds;
        try {
            autoDetect = PerformanceConfig.COMPAT_TACZ_AUTO_DETECT.get();
            bypassCap = PerformanceConfig.COMPAT_TACZ_BYPASS_CALAMITY_CAP.get();
            bypassAdaptation = PerformanceConfig.COMPAT_TACZ_BYPASS_SPORESRP_ADAPTATION.get();
            capIds = PerformanceConfig.COMPAT_TACZ_CALAMITY_CAP_GUNS.get();
            adaptationIds = PerformanceConfig.COMPAT_TACZ_ADAPTATION_BYPASS_GUNS.get();
        } catch (IllegalStateException notBoundYet) {
            // A config Loading event can precede ConfigValue binding in dev/client launches.
            return;
        } catch (RuntimeException malformedConfig) {
            SporePerformance.LOGGER.warn("TACZ damage bypass configuration is invalid; integration disabled", malformedConfig);
            kineticBulletClass = null;
            gunIdHandle = null;
            snapshot = Snapshot.disabled("invalid configuration");
            return;
        }

        if (!autoDetect) {
            kineticBulletClass = null;
            gunIdHandle = null;
            snapshot = Snapshot.disabled("autoDetect=false");
            return;
        }
        if (!ModList.get().isLoaded("tacz")) {
            snapshot = Snapshot.disabled("tacz absent");
            kineticBulletClass = null;
            gunIdHandle = null;
            return;
        }

        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) throw new ClassNotFoundException("context classloader unavailable");
            Class<?> bulletClass = Class.forName(TACZ_BULLET_CLASS, false, loader);
            MethodHandle handle = MethodHandles.publicLookup().findVirtual(
                    bulletClass, "getGunId", MethodType.methodType(ResourceLocation.class));
            Set<String> capSet = normalize(capIds);
            Set<String> adaptationSet = normalize(adaptationIds);
            kineticBulletClass = bulletClass;
            gunIdHandle = handle;
            snapshot = new Snapshot(true, bypassCap, bypassAdaptation, capSet, adaptationSet, "active");
            SporePerformance.LOGGER.info("TACZ damage bypass active: capGuns={}, adaptationGuns={}, cap={}, adaptation={}",
                    capSet.size(), adaptationSet.size(), bypassCap, bypassAdaptation);
        } catch (ReflectiveOperationException | LinkageError incompatible) {
            kineticBulletClass = null;
            gunIdHandle = null;
            snapshot = Snapshot.disabled("TACZ bullet/getGunId signature incompatible");
            SporePerformance.LOGGER.warn("TACZ detected but EntityKineticBullet#getGunId is incompatible; damage bypass disabled", incompatible);
        }
    }

    /** Clears thread-local and optional-class state when the server stops. */
    public static void clear() {
        CALAMITY_CONTEXT.remove();
        kineticBulletClass = null;
        gunIdHandle = null;
        snapshot = Snapshot.disabled("server stopped");
    }

    /** Opens a nested-safe context for the Calamity.hurt Mixin's cap redirects. */
    public static void beginCalamityDamage(Calamity calamity, DamageSource source) {
        Snapshot current = snapshot;
        boolean bypass = current.active && current.bypassCap && matches(source, current.capGunIds);
        boolean bypassAdaptation = current.active && current.bypassAdaptation
                && matches(source, current.adaptationGunIds);
        CALAMITY_CONTEXT.get().push(new Context(calamity, bypass, bypassAdaptation));
    }

    /** Closes the context even when Forge handlers cause nested damage. */
    public static void endCalamityDamage(Calamity calamity) {
        ArrayDeque<Context> stack = CALAMITY_CONTEXT.get();
        if (!stack.isEmpty()) {
            Context top = stack.peek();
            if (top.calamity == calamity) stack.pop();
            else {
                // A defensive identity removal keeps a stale context from leaking
                // into a later damage event if an optional handler re-enters a
                // different Calamity before the outer callback returns.
                Iterator<Context> iterator = stack.iterator();
                while (iterator.hasNext()) {
                    if (iterator.next().calamity == calamity) {
                        iterator.remove();
                        break;
                    }
                }
            }
        }
        if (stack.isEmpty()) CALAMITY_CONTEXT.remove();
    }

    /** Called by the three getDamageCap redirects in Calamity.hurt. */
    public static boolean bypassCurrentCalamityCap(Calamity calamity) {
        ArrayDeque<Context> stack = CALAMITY_CONTEXT.get();
        Context context = stack.peek();
        boolean bypass = context != null && context.calamity == calamity && context.bypassCap;
        if (bypass) PerformanceMetrics.increment("compat.tacz.calamity_cap_bypassed");
        return bypass;
    }

    /** Called by Spore's innate Sieger/Hohlfresser/Grakensenker adaptation redirects. */
    public static boolean bypassCurrentCalamityAdaptation(Calamity calamity) {
        ArrayDeque<Context> stack = CALAMITY_CONTEXT.get();
        Context context = stack.peek();
        boolean bypass = context != null && context.calamity == calamity && context.bypassAdaptation;
        if (bypass) PerformanceMetrics.increment("compat.tacz.spore_adaptation_bypassed");
        return bypass;
    }

    /** Called at the head of sporesrp's AdaptationEvents.onLivingHurt. */
    public static boolean bypassSrpAdaptation(DamageSource source) {
        Snapshot current = snapshot;
        boolean bypass = current.active && current.bypassAdaptation && matches(source, current.adaptationGunIds);
        if (bypass) PerformanceMetrics.increment("compat.tacz.srp_adaptation_bypassed");
        return bypass;
    }

    public static List<String> statusLines() {
        Snapshot current = snapshot;
        return List.of("TACZ damage bypass: state=" + current.state
                + ", cap=" + current.bypassCap + " (" + current.capGunIds.size() + " guns)"
                + ", sporeAndSrpAdaptation=" + current.bypassAdaptation + " (" + current.adaptationGunIds.size() + " guns)");
    }

    private static Set<String> normalize(List<? extends String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (value == null) continue;
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            ResourceLocation parsed = ResourceLocation.tryParse(normalized);
            if (parsed != null) result.add(parsed.toString());
            else SporePerformance.LOGGER.warn("Ignoring invalid TACZ gun ID in Spore Performance config: {}", value);
        }
        return Collections.unmodifiableSet(result);
    }

    private static boolean matches(DamageSource source, Set<String> allowedGunIds) {
        if (source == null || allowedGunIds.isEmpty()) return false;
        Entity direct = source.getDirectEntity();
        Class<?> bulletClass = kineticBulletClass;
        MethodHandle handle = gunIdHandle;
        if (direct == null || bulletClass == null || handle == null || !bulletClass.isInstance(direct)) return false;
        try {
            Object gunId = handle.invoke(direct);
            return gunId != null && allowedGunIds.contains(gunId.toString().toLowerCase(Locale.ROOT));
        } catch (Throwable incompatibleRuntime) {
            // A changed optional class must fail closed instead of breaking combat/server ticks.
            kineticBulletClass = null;
            gunIdHandle = null;
            Snapshot current = snapshot;
            if (current.active) snapshot = Snapshot.disabled("TACZ getGunId invocation failed");
            SporePerformance.LOGGER.warn("TACZ gun ID lookup failed; damage bypass disabled", incompatibleRuntime);
            return false;
        }
    }

    private record Context(Calamity calamity, boolean bypassCap, boolean bypassAdaptation) {}

    private record Snapshot(boolean active, boolean bypassCap, boolean bypassAdaptation,
                            Set<String> capGunIds, Set<String> adaptationGunIds, String state) {
        private static Snapshot disabled(String state) {
            return new Snapshot(false, false, false, Set.of(), Set.of(), state);
        }
    }

    private TaczDamageBypass() {}
}
