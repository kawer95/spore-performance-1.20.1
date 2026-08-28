package com.arxyt.sporeperformance.compat;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects optional integrations once per server lifetime. The checks load classes without
 * initialising them so a missing or drifted optional mod cannot break the base Spore path.
 */
public final class OptionalCompatProbe {
    private static final Map<String, State> STATES = new LinkedHashMap<>();
    private static volatile boolean aiFixHowitzerReady;
    private static volatile boolean sporesrpReady;
    private static volatile MethodHandle aiFixTrajectoryMethod;

    public enum State { ACTIVE, SKIPPED, INCOMPATIBLE }

    public static void refresh() {
        STATES.clear();
        aiFixTrajectoryMethod = null;
        aiFixHowitzerReady = probeAiFix();
        sporesrpReady = probeSporeSrp();
    }

    private static boolean probeAiFix() {
        if (!read(PerformanceConfig.COMPAT_SPOREFIX_AUTO_DETECT) || !ModList.get().isLoaded("exhuashan_sporeai_fix")) {
            STATES.put("sporefix", State.SKIPPED);
            return false;
        }
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> access = Class.forName("com.exhuashan.sporeaifix.access.HowitzerTrajectoryAccess", false, loader);
            Class<?> living = Class.forName("net.minecraft.world.entity.LivingEntity", false, loader);
            Method method = access.getMethod("sporeAiFix$hasFiringSolution", living);
            if (method.getReturnType() != boolean.class) throw new NoSuchMethodException(method.toString());
            aiFixTrajectoryMethod = MethodHandles.publicLookup().findVirtual(access, "sporeAiFix$hasFiringSolution", MethodType.methodType(boolean.class, living));
            STATES.put("sporefix", State.ACTIVE);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            STATES.put("sporefix", State.INCOMPATIBLE);
            SporePerformance.LOGGER.warn("AI Fix detected but its Howitzer signature is incompatible; integration disabled", exception);
            return false;
        }
    }

    private static boolean probeSporeSrp() {
        if (!read(PerformanceConfig.COMPAT_SPORESRP_AUTO_DETECT) || !ModList.get().isLoaded("sporesrp")) {
            STATES.put("sporesrp", State.SKIPPED);
            return false;
        }
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> proto = Class.forName("com.maha_fish.sporesrp.handler.ProtoSkillsHandler", false, loader);
            Class<?> marked = Class.forName("com.maha_fish.sporesrp.handler.ProtoMarkedMoundHandler", false, loader);
            Class<?> full = Class.forName("com.maha_fish.sporesrp.handler.FullHivemindSkillsHandler", false, loader);
            Class<?> mining = Class.forName("com.maha_fish.sporesrp.handler.FullHivemindHandler", false, loader);
            proto.getDeclaredMethod("onServerTick", Class.forName("net.minecraftforge.event.TickEvent$ServerTickEvent", false, loader));
            marked.getDeclaredMethod("onServerTick", Class.forName("net.minecraftforge.event.TickEvent$ServerTickEvent", false, loader));
            full.getDeclaredMethod("onServerTick", Class.forName("net.minecraftforge.event.TickEvent$ServerTickEvent", false, loader));
            proto.getDeclaredMethod("scanForSurface", ServerLevel.class, BlockPos.class, int.class);
            full.getDeclaredMethod("scanForSurface", ServerLevel.class, BlockPos.class, int.class);
            mining.getDeclaredMethod("generateSphereQueue", BlockPos.class, int.class);
            mining.getDeclaredMethod("buildCasings", ServerLevel.class, BlockPos.class);
            mining.getDeclaredMethod("buildCasingsOnce", ServerLevel.class, BlockPos.class);
            STATES.put("sporesrp", State.ACTIVE);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            STATES.put("sporesrp", State.INCOMPATIBLE);
            SporePerformance.LOGGER.warn("sporesrp detected but handler signatures are incompatible; integration disabled", exception);
            return false;
        }
    }

    public static boolean aiFixHowitzerReady() { return aiFixHowitzerReady; }
    public static boolean sporesrpReady() { return sporesrpReady; }

    public static State state(String integration) { return STATES.getOrDefault(integration, State.SKIPPED); }

    /** A cached handle proves the checked AI Fix signature remains available without hot-path reflection. */
    public static MethodHandle aiFixTrajectoryMethod() { return aiFixTrajectoryMethod; }

    public static String summary() {
        return "spore=" + version("spore")
                + ", sporefix=" + state("sporefix") + "(" + version("exhuashan_sporeai_fix") + ")"
                + ", sporesrp=" + state("sporesrp") + "(" + version("sporesrp") + ")"
                + ", patches=" + String.join(",", patchStates());
    }

    public static List<String> statusLines() {
        return List.of(
                "Spore Performance: spore=" + version("spore") + ", sporefix=" + state("sporefix") + "(" + version("exhuashan_sporeai_fix") + "), sporesrp=" + state("sporesrp") + "(" + version("sporesrp") + ")",
                "Safe: " + String.join(", ", safePatchStates()),
                "Aggressive: " + String.join(", ", aggressivePatchStates()),
                "Client: hinderburg-index=" + enabled(read(PerformanceConfig.CLIENT_HINDERBURG_INDEX))
                        + ", sporesrp-hud=" + patched("OptionalSporeSrpHudMixin", read(PerformanceConfig.CLIENT_SPORESRP_HUD_HOTBAR))
        );
    }

    private static List<String> patchStates() {
        List<String> states = new ArrayList<>(safePatchStates());
        states.addAll(aggressivePatchStates());
        return states;
    }

    private static List<String> safePatchStates() {
        return List.of(
                "spawner-index=" + enabled(read(PerformanceConfig.SAFE_SPAWNER_SERVER_ONLY)),
                "infection-map=" + enabled(read(PerformanceConfig.SAFE_COMPILED_INFECTION_MAP)),
                "tendril-spread-fast-path=" + enabled(read(PerformanceConfig.SAFE_TENDRIL_SPREAD_FAST_PATH)),
                "calamity-path-gate=" + enabled(read(PerformanceConfig.SAFE_SAME_TICK_PATH_GATE)),
                "calamity-follow=" + enabled(read(PerformanceConfig.SAFE_SKIP_NON_EVOLVING_CALAMITY_FOLLOW)),
                "howitzer-los=" + patched("OptionalHowitzerMixin", read(PerformanceConfig.SAFE_HOWITZER_SAME_TICK_CACHE)),
                "hinderburg-index=" + patched("OptionalStormFortressClientMixin", read(PerformanceConfig.CLIENT_HINDERBURG_INDEX)),
                "sporesrp-hud=" + patched("OptionalSporeSrpHudMixin", read(PerformanceConfig.CLIENT_SPORESRP_HUD_HOTBAR)),
                "sporesrp-dimension-buckets=" + patched("OptionalSporeSrpProtoSkillsMixin", read(PerformanceConfig.SAFE_SPORESRP_DIMENSION_GUARDS)),
                "sporesrp-disabled-gate=" + gated("sporesrp", read(PerformanceConfig.SAFE_SPORESRP_DISABLED_SHORT_CIRCUIT)),
                "sporesrp-builder-buckets=" + patched("OptionalSporeSrpBuilderMixin", read(PerformanceConfig.SAFE_SPORESRP_DIMENSION_GUARDS))
        );
    }

    private static List<String> aggressivePatchStates() {
        return List.of(
                "tendril-cursor=" + aggressive(read(PerformanceConfig.AGGRESSIVE_MOUND_TENDRIL)),
                "foliage-cursor=" + aggressive(read(PerformanceConfig.AGGRESSIVE_FOLIAGE)),
                "foliage-fast-cursor=" + aggressive(read(PerformanceConfig.AGGRESSIVE_FOLIAGE_FAST_CURSOR)),
                "foliage-direct-chunk=" + aggressive(read(PerformanceConfig.AGGRESSIVE_FOLIAGE_DIRECT_CHUNK_READ)),
                "foliage-time-budget=" + aggressive(read(PerformanceConfig.AGGRESSIVE_FOLIAGE_TIME_BUDGET)),
                "tendril-time-budget=" + aggressive(read(PerformanceConfig.AGGRESSIVE_TENDRIL_TIME_BUDGET)),
                "group-sensing=" + aggressive(read(PerformanceConfig.AGGRESSIVE_GROUP_SENSING)),
                "follow-snapshot=" + aggressive(read(PerformanceConfig.AGGRESSIVE_GROUP_SENSING)),
                "follow-path-reuse=" + aggressive(read(PerformanceConfig.AGGRESSIVE_FOLLOW_PATH_REUSE)),
                "follow-path-backoff=" + aggressive(read(PerformanceConfig.AGGRESSIVE_FOLLOW_PATH_BACKOFF)),
                "calamity-backoff=" + aggressive(read(PerformanceConfig.AGGRESSIVE_PATH_BACKOFF)),
                "remote-idle-ai=" + aggressive(read(PerformanceConfig.AGGRESSIVE_REMOTE_IDLE_AI)),
                "howitzer-trajectory=" + aggressivePatched("OptionalHowitzerMixin", read(PerformanceConfig.AGGRESSIVE_HOWITZER_CACHE)),
                "sporesrp-lazy-mining=" + aggressivePatched("OptionalSporeSrpFullHivemindMiningMixin", read(PerformanceConfig.AGGRESSIVE_SPORESRP_LAZY_HIVEMIND_QUEUE)),
                "sporesrp-mining-budget=" + aggressivePatched("OptionalSporeSrpFullHivemindMiningMixin", read(PerformanceConfig.AGGRESSIVE_SPORESRP_MINING_BUDGET)),
                "sporesrp-surface-cursor=" + aggressivePatched("OptionalSporeSrpProtoSkillsMixin", read(PerformanceConfig.AGGRESSIVE_SPORESRP_SURFACE_SEARCH)),
                "sporesrp-casing-cursor=" + aggressivePatched("OptionalSporeSrpFullHivemindMiningMixin", read(PerformanceConfig.AGGRESSIVE_SPORESRP_CASING_SCHEDULER)),
                "sporesrp-uuid-stagger=" + aggressive("sporesrp", read(PerformanceConfig.AGGRESSIVE_SPORESRP_PROTO_STAGGER, 1) > 1
                        || read(PerformanceConfig.AGGRESSIVE_SPORESRP_FULL_HIVEMIND_STAGGER, 1) > 1 || read(PerformanceConfig.AGGRESSIVE_SPORESRP_BUILDER_STAGGER, 1) > 1),
                "sporefix-permanent-audit=" + aggressivePatched("OptionalImmortalAuditMixin", read(PerformanceConfig.AGGRESSIVE_SPOREFIX_PERMANENT_AUDIT))
        );
    }

    private static boolean read(ForgeConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            // Forge can fire ServerStarting before a development config has
            // finished binding.  Status output must never take the server
            // down; the normal runtime path reads values after config load.
            return false;
        }
    }

    private static int read(ForgeConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return fallback;
        }
    }

    private static String enabled(boolean active) { return active ? State.ACTIVE.name() : State.SKIPPED.name(); }
    private static String gated(String module, boolean enabled) { return enabled && state(module) == State.ACTIVE ? State.ACTIVE.name() : (state(module) == State.INCOMPATIBLE ? State.INCOMPATIBLE.name() : State.SKIPPED.name()); }
    private static String aggressive(boolean enabled) { return enabled ? State.ACTIVE.name() : State.SKIPPED.name(); }
    private static String aggressive(String module, boolean enabled) { return enabled ? gated(module, true) : (state(module) == State.INCOMPATIBLE ? State.INCOMPATIBLE.name() : State.SKIPPED.name()); }
    private static String patched(String patch, boolean enabled) { return enabled ? MixinPatchStatus.state(patch).name() : State.SKIPPED.name(); }
    private static String aggressivePatched(String patch, boolean enabled) { return enabled ? MixinPatchStatus.state(patch).name() : State.SKIPPED.name(); }

    private static String version(String id) {
        return ModList.get().getModContainerById(id).map(container -> container.getModInfo().getVersion().toString()).orElse("absent");
    }

    private OptionalCompatProbe() {}
}
