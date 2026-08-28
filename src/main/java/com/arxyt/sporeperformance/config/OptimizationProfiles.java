package com.arxyt.sporeperformance.config;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.compat.OptionalCompatProbe;
import com.arxyt.sporeperformance.compat.TaczDamageBypass;
import com.arxyt.sporeperformance.world.InfectionConversionCache;
import com.arxyt.sporeperformance.world.RemoteIdleAiController;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * User-facing optimization presets.  Presets deliberately change only boolean
 * optimization switches; limits, TACZ damage policy, diagnostics and numeric
 * tuning values remain administrator-owned settings.
 *
 * <p>The manager is usable from both the integrated server and a dedicated
 * server command.  Client configuration is applied separately by the client
 * screen so a multiplayer client never silently edits the remote server's
 * common configuration.</p>
 */
public final class OptimizationProfiles {
    public enum Profile {
        NORMAL("常规", "只启用安全优化"),
        AGGRESSIVE("激进", "安全优化、AI 重构和已配置的激进优化"),
        ALL("全部", "启用所有可切换优化（包含本模组掉落物合并）");

        private final String displayName;
        private final String description;

        Profile(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }

        public static Profile parse(String value) {
            if (value == null) return null;
            return switch (value.toLowerCase(java.util.Locale.ROOT)) {
                case "normal", "safe", "常规" -> NORMAL;
                case "aggressive", "激进" -> AGGRESSIVE;
                case "all", "全部", "full" -> ALL;
                default -> null;
            };
        }
    }

    public enum Scope {
        COMMON,
        CLIENT,
        BOTH
    }

    public record Result(Profile profile, Scope scope, int changedValues,
                         boolean restartRequired, List<String> restartReasons) {
        public Result {
            restartReasons = List.copyOf(restartReasons);
        }

        public String summary() {
            StringBuilder message = new StringBuilder("已切换到").append(profile.displayName())
                    .append("预设，修改 ").append(changedValues).append(" 项配置");
            if (scope == Scope.CLIENT) message.append("（仅客户端）");
            if (scope == Scope.COMMON) message.append("（仅服务端通用配置）");
            if (restartRequired) {
                message.append("；以下项目将在重启后完整生效：");
                message.append(String.join("、", restartReasons));
            } else {
                message.append("；运行中的可热重载项目已立即生效。");
            }
            return message.toString();
        }
    }

    private static final Object LOCK = new Object();

    /* Safe common switches. */
    private static final List<ForgeConfigSpec.BooleanValue> COMMON_SAFE = List.of(
            PerformanceConfig.SAFE_SPAWNER_SERVER_ONLY,
            PerformanceConfig.SAFE_COMPILED_INFECTION_MAP,
            PerformanceConfig.SAFE_TENDRIL_SPREAD_FAST_PATH,
            PerformanceConfig.SAFE_SAME_TICK_PATH_GATE,
            PerformanceConfig.SAFE_SKIP_NON_EVOLVING_CALAMITY_FOLLOW,
            PerformanceConfig.SAFE_HOWITZER_SAME_TICK_CACHE,
            PerformanceConfig.SAFE_SPORESRP_DIMENSION_GUARDS,
            PerformanceConfig.SAFE_SPORESRP_DISABLED_SHORT_CIRCUIT,
            PerformanceConfig.SAFE_SONA_CAN_CHUNK_CACHE,
            PerformanceConfig.SAFE_SPORE_PROJECTILE_BROADPHASE,
            PerformanceConfig.SAFE_PERSIST_BILE_PROJECTILE_LIFETIME);

    /* All common AI/navigation/foliage refactor switches. */
    private static final List<ForgeConfigSpec.BooleanValue> COMMON_REFACTOR = List.of(
            PerformanceConfig.REFACTOR_AI_ENABLED,
            PerformanceConfig.REFACTOR_SHARED_PERCEPTION,
            PerformanceConfig.REFACTOR_EVENT_THREATS,
            PerformanceConfig.REFACTOR_GROUP_COORDINATION,
            PerformanceConfig.REFACTOR_TICK_PIPELINE,
            PerformanceConfig.REFACTOR_NAVIGATION_ENABLED,
            PerformanceConfig.REFACTOR_SHARED_CORRIDORS,
            PerformanceConfig.REFACTOR_ASYNC_LONG_PATHS,
            PerformanceConfig.REFACTOR_COMPAT_SPOREFIX,
            PerformanceConfig.REFACTOR_COMPAT_SPORESRP,
            PerformanceConfig.REFACTOR_CALAMITY_NAVIGATION_ENABLED,
            PerformanceConfig.REFACTOR_CALAMITY_SINGLE_YAW_OWNER,
            PerformanceConfig.REFACTOR_CALAMITY_PROGRESS_RECOVERY,
            PerformanceConfig.REFACTOR_CALAMITY_POSITION_PATH_CACHE,
            PerformanceConfig.REFACTOR_CALAMITY_SECTION_PATH_INVALIDATION,
            PerformanceConfig.REFACTOR_CALAMITY_EXCLUDE_VERFALLDRACHEN,
            PerformanceConfig.REFACTOR_MULTIPART_MINIMAL_TICK,
            PerformanceConfig.REFACTOR_MULTIPART_SHARED_MELEE_QUERY,
            PerformanceConfig.REFACTOR_MOUND_MINIMAL_TICK,
            PerformanceConfig.REFACTOR_ROOTED_GASTGEBER_MINIMAL_TICK,
            PerformanceConfig.REFACTOR_ENFORCE_WORK_TOKENS_BEFORE_AI,
            PerformanceConfig.REFACTOR_FOLLOW_GROUP_PATHING,
            PerformanceConfig.REFACTOR_FOLLOW_SIZE_AWARE_ARRIVAL,
            PerformanceConfig.REFACTOR_BUSSER_ENABLED,
            PerformanceConfig.REFACTOR_BUSSER_VARIANT_GOAL_PRUNING,
            PerformanceConfig.REFACTOR_BUSSER_SHARED_AIR_SWEEP_CONTEXT,
            PerformanceConfig.REFACTOR_FOLIAGE_LAZY_NEIGHBOR_READS,
            PerformanceConfig.REFACTOR_FOLIAGE_COMPILED_ACTION_PLANS);

    /* Boolean switches in the common [aggressive] section. */
    private static final List<ForgeConfigSpec.BooleanValue> COMMON_AGGRESSIVE = List.of(
            PerformanceConfig.AGGRESSIVE_MOUND_TENDRIL,
            PerformanceConfig.AGGRESSIVE_FOLIAGE,
            PerformanceConfig.AGGRESSIVE_FOLIAGE_FAST_CURSOR,
            PerformanceConfig.AGGRESSIVE_FOLIAGE_DIRECT_CHUNK_READ,
            PerformanceConfig.AGGRESSIVE_FOLIAGE_TIME_BUDGET,
            PerformanceConfig.AGGRESSIVE_TENDRIL_TIME_BUDGET,
            PerformanceConfig.AGGRESSIVE_PATH_BACKOFF,
            PerformanceConfig.AGGRESSIVE_BALANCED_TARGETING,
            PerformanceConfig.AGGRESSIVE_GENERAL_PATH_BACKOFF,
            PerformanceConfig.AGGRESSIVE_STATIONARY_ITEM_PHYSICS_LOD,
            PerformanceConfig.AGGRESSIVE_ORPHAN_PROJECTILE_CLEANUP,
            PerformanceConfig.AGGRESSIVE_REMOTE_IDLE_AI,
            PerformanceConfig.AGGRESSIVE_HOWITZER_CACHE,
            PerformanceConfig.AGGRESSIVE_GROUP_SENSING,
            PerformanceConfig.AGGRESSIVE_FOLLOW_PATH_REUSE,
            PerformanceConfig.AGGRESSIVE_FOLLOW_PATH_BACKOFF,
            PerformanceConfig.AGGRESSIVE_SPORESRP_LAZY_HIVEMIND_QUEUE,
            PerformanceConfig.AGGRESSIVE_SPORESRP_MINING_BUDGET,
            PerformanceConfig.AGGRESSIVE_SPORESRP_SURFACE_SEARCH,
            PerformanceConfig.AGGRESSIVE_SPORESRP_CASING_SCHEDULER,
            PerformanceConfig.AGGRESSIVE_SPOREFIX_PERMANENT_AUDIT);

    /* Other common performance toggles, intentionally excluding policy controls. */
    private static final List<ForgeConfigSpec.BooleanValue> COMMON_EXTRA = List.of(
            PerformanceConfig.ITEM_LIFETIME_ENABLED,
            PerformanceConfig.COMPAT_TOUHOU_POWER_POINT_OPTIMIZATION);

    /* Safe client switches. HUD placement and visual policy are not changed by a preset. */
    private static final List<ForgeConfigSpec.BooleanValue> CLIENT_SAFE = List.of(
            PerformanceConfig.CLIENT_HINDERBURG_INDEX,
            PerformanceConfig.CLIENT_DEFER_ILLUSION_ENTITY_CREATION,
            PerformanceConfig.CLIENT_CACHE_ILLUSION_ENTITY_TYPES,
            PerformanceConfig.CLIENT_SKIP_DUPLICATE_LAYER_ANIMATION,
            PerformanceConfig.CLIENT_FIX_SONA_INFECTION_POST_DEPTH,
            PerformanceConfig.CLIENT_SONA_SHARE_FRAME_SAMPLE,
            PerformanceConfig.CLIENT_SONA_BATCH_OVERLAY_QUADS,
            PerformanceConfig.CLIENT_SONA_PRECOMPUTE_OVERLAY_SEEDS);

    /* Visual and client-side aggressive switches. */
    private static final List<ForgeConfigSpec.BooleanValue> CLIENT_AGGRESSIVE = List.of(
            PerformanceConfig.CLIENT_FUNGAL_DECORATION_DISTANCE_CULL,
            PerformanceConfig.CLIENT_EYE_DISTANCE_CULL,
            PerformanceConfig.CLIENT_TRANSLUCENT_DISTANCE_CULL,
            PerformanceConfig.CLIENT_EMISSIVE_DISTANCE_CULL,
            PerformanceConfig.CLIENT_CALAMITY_EFFECT_CULL,
            PerformanceConfig.CLIENT_ORGANOID_EFFECT_CULL,
            PerformanceConfig.CLIENT_HYPER_EFFECT_CULL,
            PerformanceConfig.CLIENT_PROTO_EFFECT_CULL,
            PerformanceConfig.CLIENT_VERIFIED_MULTI_ROOT_PART_MASK,
            PerformanceConfig.CLIENT_EYE_OPAQUE_PART_MASK,
            PerformanceConfig.CLIENT_EMISSIVE_OPAQUE_PART_MASK,
            PerformanceConfig.CLIENT_ANIMATION_LOD,
            PerformanceConfig.CLIENT_MAJOR_ANIMATION_LOD,
            PerformanceConfig.CLIENT_CALAMITY_ANIMATION_LOD,
            PerformanceConfig.CLIENT_ORGANOID_ANIMATION_LOD,
            PerformanceConfig.CLIENT_HYPER_ANIMATION_LOD,
            PerformanceConfig.CLIENT_PROTO_ANIMATION_LOD,
            PerformanceConfig.CLIENT_SONA_OVERLAY_GEOMETRY_LOD,
            PerformanceConfig.CLIENT_SONA_OVERLAY_PARTICLE_SCALE_ENABLED,
            PerformanceConfig.CLIENT_SONA_POST_HALF_RESOLUTION,
            PerformanceConfig.CLIENT_ACCELERATE_EMISSIVE_LAYERS,
            PerformanceConfig.CLIENT_ACCELERATE_TRANSLUCENT_LAYERS);

    private OptimizationProfiles() {}

    /** Applies a common (server-owned) preset and refreshes runtime snapshots. */
    public static Result applyCommon(Profile profile) {
        return applySafely(profile, Scope.COMMON);
    }

    /** Applies a client-only preset. */
    public static Result applyClient(Profile profile) {
        return applySafely(profile, Scope.CLIENT);
    }

    /** Applies both specs. Use only for an integrated server or a local menu. */
    public static Result applyBoth(Profile profile) {
        return applySafely(profile, Scope.BOTH);
    }

    public static Profile detectCommon() {
        synchronized (LOCK) {
            if (allEnabled(COMMON_SAFE) && allEnabled(COMMON_REFACTOR)
                    && allEnabled(COMMON_AGGRESSIVE) && allEnabled(COMMON_EXTRA)
                    && !PerformanceConfig.ITEM_MERGE_ENABLED.get()) return Profile.AGGRESSIVE;
            if (allEnabled(COMMON_SAFE) && !anyEnabled(COMMON_REFACTOR)
                    && !anyEnabled(COMMON_AGGRESSIVE) && !anyEnabled(COMMON_EXTRA)
                    && !PerformanceConfig.ITEM_MERGE_ENABLED.get()) return Profile.NORMAL;
            if (allEnabled(COMMON_SAFE) && allEnabled(COMMON_REFACTOR)
                    && allEnabled(COMMON_AGGRESSIVE) && allEnabled(COMMON_EXTRA)
                    && PerformanceConfig.ITEM_MERGE_ENABLED.get()) return Profile.ALL;
            return null;
        }
    }

    public static Profile detectCommonSafely() {
        try {
            return detectCommon();
        } catch (RuntimeException notBoundYet) {
            return null;
        }
    }

    public static Profile detectClient() {
        synchronized (LOCK) {
            if (allEnabled(CLIENT_SAFE) && allEnabled(CLIENT_AGGRESSIVE)) return Profile.ALL;
            if (allEnabled(CLIENT_SAFE) && !anyEnabled(CLIENT_AGGRESSIVE)) return Profile.NORMAL;
            if (allEnabled(CLIENT_SAFE) && anyEnabled(CLIENT_AGGRESSIVE)) return Profile.AGGRESSIVE;
            return null;
        }
    }

    public static Profile detectClientSafely() {
        try {
            return detectClient();
        } catch (RuntimeException notBoundYet) {
            return null;
        }
    }

    /** Returns the combined profile only when both specs agree. */
    public static Profile detectCombined() {
        Profile common = detectCommon();
        Profile client = detectClient();
        return common != null && common == client ? common : null;
    }

    /** Detection is also used by the title-screen GUI, before every config is bound. */
    public static Profile detectCombinedSafely() {
        try {
            return detectCombined();
        } catch (RuntimeException notBoundYet) {
            return null;
        }
    }

    private static Result applySafely(Profile profile, Scope scope) {
        try {
            return apply(profile, scope);
        } catch (RuntimeException exception) {
            SporePerformance.LOGGER.warn("Unable to apply Spore Performance {} preset before config was loaded",
                    profile == null ? "<null>" : profile.displayName(), exception);
            return new Result(profile == null ? Profile.NORMAL : profile, scope, 0, true,
                    List.of("配置尚未加载，请进入世界后重试或重启游戏"));
        }
    }

    private static Result apply(Profile profile, Scope scope) {
        if (profile == null) throw new IllegalArgumentException("profile");
        synchronized (LOCK) {
            int changed = 0;
            List<String> restartReasons = new ArrayList<>();
            boolean advanced = profile != Profile.NORMAL;
            boolean all = profile == Profile.ALL;
            boolean oldAcceleratedEmissive = safeGet(PerformanceConfig.CLIENT_ACCELERATE_EMISSIVE_LAYERS);
            boolean oldAcceleratedTranslucent = safeGet(PerformanceConfig.CLIENT_ACCELERATE_TRANSLUCENT_LAYERS);

            if (scope == Scope.COMMON || scope == Scope.BOTH) {
                changed += setAll(COMMON_SAFE, true);
                changed += setAll(COMMON_REFACTOR, advanced);
                changed += setAll(COMMON_AGGRESSIVE, advanced);
                changed += setAll(COMMON_EXTRA, advanced);
                // Harium/Lithium is the normal owner of item merging. Only an
                // explicit "全部" preset turns on this add-on's merger.
                changed += set(PerformanceConfig.ITEM_MERGE_ENABLED, all);
                if (changed > 0) {
                    PerformanceConfig.COMMON_SPEC.save();
                    refreshCommonRuntime();
                }
            }

            int clientChanged = 0;
            if (scope == Scope.CLIENT || scope == Scope.BOTH) {
                clientChanged += setAll(CLIENT_SAFE, true);
                clientChanged += setAll(CLIENT_AGGRESSIVE, advanced);
                if (clientChanged > 0) PerformanceConfig.CLIENT_SPEC.save();
                changed += clientChanged;
            }

            // AcceleratedRendering's handles are resolved at client startup.
            // The boolean itself is safe to write immediately, but tell the
            // user that a restart is needed if it changed from the old state.
            if ((scope == Scope.CLIENT || scope == Scope.BOTH)
                    && (oldAcceleratedEmissive != safeGet(PerformanceConfig.CLIENT_ACCELERATE_EMISSIVE_LAYERS)
                    || oldAcceleratedTranslucent != safeGet(PerformanceConfig.CLIENT_ACCELERATE_TRANSLUCENT_LAYERS))) {
                restartReasons.add("AcceleratedRendering 管线");
            }
            return new Result(profile, scope, changed, !restartReasons.isEmpty(), restartReasons);
        }
    }

    /* ConfigValue#set does not expose a changed flag, so this helper compares the old value. */
    private static int set(ForgeConfigSpec.BooleanValue value, boolean desired) {
        boolean changed = safeGet(value) != desired;
        value.set(desired);
        return changed ? 1 : 0;
    }

    private static int setAll(List<ForgeConfigSpec.BooleanValue> values, boolean desired) {
        int changed = 0;
        for (ForgeConfigSpec.BooleanValue value : values) changed += set(value, desired);
        return changed;
    }

    private static boolean allEnabled(List<ForgeConfigSpec.BooleanValue> values) {
        for (ForgeConfigSpec.BooleanValue value : values) if (!safeGet(value)) return false;
        return true;
    }

    private static boolean anyEnabled(List<ForgeConfigSpec.BooleanValue> values) {
        for (ForgeConfigSpec.BooleanValue value : values) if (safeGet(value)) return true;
        return false;
    }

    private static boolean safeGet(ForgeConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notBoundYet) {
            return value.getDefault();
        }
    }

    private static void refreshCommonRuntime() {
        try {
            InfectionConversionCache.refresh();
            RemoteIdleAiController.refreshFromConfig();
            OptionalCompatProbe.refresh();
            TaczDamageBypass.refresh();
        } catch (RuntimeException exception) {
            // A profile must never make a running server unusable. ConfigValue
            // writes are already complete; a later lifecycle event retries the
            // normal snapshot refresh.
            SporePerformance.LOGGER.warn("Failed to refresh one runtime snapshot after applying preset", exception);
        }
    }
}
