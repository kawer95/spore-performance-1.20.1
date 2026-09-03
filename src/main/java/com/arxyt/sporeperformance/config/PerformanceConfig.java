package com.arxyt.sporeperformance.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Runtime configuration. Values are read at scheduling boundaries so reloads affect new work
 * without mutating an in-flight task's captured budget and radius.
 */
public final class PerformanceConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.BooleanValue SAFE_SPAWNER_SERVER_ONLY;
    public static final ForgeConfigSpec.BooleanValue SAFE_COMPILED_INFECTION_MAP;
    public static final ForgeConfigSpec.BooleanValue SAFE_TENDRIL_SPREAD_FAST_PATH;
    public static final ForgeConfigSpec.BooleanValue SAFE_SAME_TICK_PATH_GATE;
    public static final ForgeConfigSpec.BooleanValue SAFE_SKIP_NON_EVOLVING_CALAMITY_FOLLOW;
    public static final ForgeConfigSpec.BooleanValue SAFE_HOWITZER_SAME_TICK_CACHE;
    public static final ForgeConfigSpec.BooleanValue SAFE_SPORESRP_DIMENSION_GUARDS;
    public static final ForgeConfigSpec.BooleanValue SAFE_SPORESRP_DISABLED_SHORT_CIRCUIT;
    public static final ForgeConfigSpec.BooleanValue SAFE_SONA_CAN_CHUNK_CACHE;
    public static final ForgeConfigSpec.BooleanValue SAFE_SPORE_PROJECTILE_BROADPHASE;
    public static final ForgeConfigSpec.BooleanValue SAFE_PERSIST_BILE_PROJECTILE_LIFETIME;
    public static final ForgeConfigSpec.IntValue SAFE_BILE_PROJECTILE_LIFETIME_TICKS;
    public static final ForgeConfigSpec.BooleanValue CLIENT_HINDERBURG_INDEX;
    public static final ForgeConfigSpec.BooleanValue CLIENT_SPORESRP_HUD_HOTBAR;
    public static final ForgeConfigSpec.BooleanValue CLIENT_SPORESRP_HUD_ABOVE_SCREENS;
    public static final ForgeConfigSpec.BooleanValue CLIENT_SPORESRP_HUD_IN_GAMEPLAY;
    public static final ForgeConfigSpec.BooleanValue CLIENT_FUNGAL_DECORATION_DISTANCE_CULL;
    public static final ForgeConfigSpec.IntValue CLIENT_FUNGAL_DECORATION_DISTANCE;
    public static final ForgeConfigSpec.IntValue CLIENT_FUNGAL_DECORATION_COMMAND_DISTANCE;
    public static final ForgeConfigSpec.IntValue CLIENT_FUNGAL_DECORATION_CAMERA_STEP;
    public static final ForgeConfigSpec.IntValue CLIENT_FUNGAL_DECORATION_REBUILDS_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue CLIENT_DEFER_ILLUSION_ENTITY_CREATION;
    public static final ForgeConfigSpec.BooleanValue CLIENT_CACHE_ILLUSION_ENTITY_TYPES;
    public static final ForgeConfigSpec.BooleanValue CLIENT_SKIP_DUPLICATE_LAYER_ANIMATION;
    public static final ForgeConfigSpec.BooleanValue CLIENT_FIX_SONA_INFECTION_POST_DEPTH;
    public static final ForgeConfigSpec.BooleanValue CLIENT_SONA_SHARE_FRAME_SAMPLE;
    public static final ForgeConfigSpec.BooleanValue CLIENT_SONA_BATCH_OVERLAY_QUADS;
    public static final ForgeConfigSpec.BooleanValue CLIENT_SONA_PRECOMPUTE_OVERLAY_SEEDS;
    public static final ForgeConfigSpec.BooleanValue CLIENT_EYE_DISTANCE_CULL;
    public static final ForgeConfigSpec.IntValue CLIENT_EYE_RENDER_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue CLIENT_TRANSLUCENT_DISTANCE_CULL;
    public static final ForgeConfigSpec.IntValue CLIENT_TRANSLUCENT_RENDER_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue CLIENT_EMISSIVE_DISTANCE_CULL;
    public static final ForgeConfigSpec.IntValue CLIENT_EMISSIVE_RENDER_DISTANCE;
    public static final ForgeConfigSpec.IntValue CLIENT_MAJOR_EFFECT_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue CLIENT_CALAMITY_EFFECT_CULL;
    public static final ForgeConfigSpec.IntValue CLIENT_CALAMITY_EFFECT_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue CLIENT_ORGANOID_EFFECT_CULL;
    public static final ForgeConfigSpec.IntValue CLIENT_ORGANOID_EFFECT_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue CLIENT_HYPER_EFFECT_CULL;
    public static final ForgeConfigSpec.IntValue CLIENT_HYPER_EFFECT_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue CLIENT_PROTO_EFFECT_CULL;
    public static final ForgeConfigSpec.IntValue CLIENT_PROTO_EFFECT_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue CLIENT_VERIFIED_MULTI_ROOT_PART_MASK;
    public static final ForgeConfigSpec.BooleanValue CLIENT_EYE_OPAQUE_PART_MASK;
    public static final ForgeConfigSpec.BooleanValue CLIENT_EMISSIVE_OPAQUE_PART_MASK;
    public static final ForgeConfigSpec.BooleanValue CLIENT_ANIMATION_LOD;
    public static final ForgeConfigSpec.IntValue CLIENT_ANIMATION_NEAR_DISTANCE;
    public static final ForgeConfigSpec.IntValue CLIENT_ANIMATION_MEDIUM_DISTANCE;
    public static final ForgeConfigSpec.IntValue CLIENT_ANIMATION_FAR_DISTANCE;
    public static final ForgeConfigSpec.IntValue CLIENT_ANIMATION_MEDIUM_INTERVAL;
    public static final ForgeConfigSpec.IntValue CLIENT_ANIMATION_FAR_INTERVAL;
    public static final ForgeConfigSpec.IntValue CLIENT_ANIMATION_VERY_FAR_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue CLIENT_MAJOR_ANIMATION_LOD;
    public static final ForgeConfigSpec.BooleanValue CLIENT_CALAMITY_ANIMATION_LOD;
    public static final ForgeConfigSpec.BooleanValue CLIENT_ORGANOID_ANIMATION_LOD;
    public static final ForgeConfigSpec.BooleanValue CLIENT_HYPER_ANIMATION_LOD;
    public static final ForgeConfigSpec.BooleanValue CLIENT_PROTO_ANIMATION_LOD;
    public static final ForgeConfigSpec.IntValue CLIENT_MAJOR_ANIMATION_NEAR_DISTANCE;
    public static final ForgeConfigSpec.IntValue CLIENT_MAJOR_ANIMATION_FAR_DISTANCE;
    public static final ForgeConfigSpec.IntValue CLIENT_MAJOR_ANIMATION_FAR_INTERVAL;
    public static final ForgeConfigSpec.IntValue CLIENT_POSE_CACHE_MAX_ENTITIES;
    public static final ForgeConfigSpec.BooleanValue CLIENT_ACCELERATED_RENDERING_AUTO_DETECT;
    public static final ForgeConfigSpec.BooleanValue CLIENT_ACCELERATE_EMISSIVE_LAYERS;
    public static final ForgeConfigSpec.BooleanValue CLIENT_ACCELERATE_TRANSLUCENT_LAYERS;
    public static final ForgeConfigSpec.BooleanValue CLIENT_SONA_OVERLAY_GEOMETRY_LOD;
    public static final ForgeConfigSpec.IntValue CLIENT_SONA_OVERLAY_UPDATE_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue CLIENT_SONA_OVERLAY_PARTICLE_SCALE_ENABLED;
    public static final ForgeConfigSpec.DoubleValue CLIENT_SONA_OVERLAY_PARTICLE_SCALE;
    public static final ForgeConfigSpec.BooleanValue CLIENT_SONA_POST_HALF_RESOLUTION;
    public static final ForgeConfigSpec.BooleanValue CLIENT_RENDER_METRICS;

    public static final ForgeConfigSpec.IntValue LIMIT_FUNGAL_UNITS_PER_DIMENSION;
    public static final ForgeConfigSpec.IntValue LIMIT_MOUNDS_PER_DIMENSION;
    public static final ForgeConfigSpec.IntValue LIMIT_TENDRILS_PER_DIMENSION;
    public static final ForgeConfigSpec.IntValue LIMIT_CALAMITY_TOTAL_PER_DIMENSION;
    public static final ForgeConfigSpec.IntValue LIMIT_CALAMITY_PER_TYPE_PER_DIMENSION;

    public static final ForgeConfigSpec.BooleanValue ITEM_MERGE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ITEM_MERGE_GLOBAL;
    public static final ForgeConfigSpec.IntValue ITEM_MERGE_RADIUS;
    public static final ForgeConfigSpec.IntValue ITEM_MERGE_INTERVAL;
    public static final ForgeConfigSpec.IntValue ITEM_MERGE_ENTITY_BUDGET;
    public static final ForgeConfigSpec.IntValue ITEM_MERGE_TIME_BUDGET_MICROS;
    public static final ForgeConfigSpec.BooleanValue ITEM_LIFETIME_ENABLED;
    public static final ForgeConfigSpec.IntValue ITEM_LIFETIME_FAST;
    public static final ForgeConfigSpec.IntValue ITEM_LIFETIME_NORMAL;
    public static final ForgeConfigSpec.IntValue ITEM_LIFETIME_PLAYER;
    public static final ForgeConfigSpec.BooleanValue ITEM_LIFETIME_PROTECT_SPECIAL;

    public static final ForgeConfigSpec.IntValue WORKING_FUNGAL_UNITS;
    public static final ForgeConfigSpec.IntValue WORKING_GASTGEBERS;
    public static final ForgeConfigSpec.IntValue WORKING_MOUNDS;
    public static final ForgeConfigSpec.IntValue WORKING_TENDRILS;
    public static final ForgeConfigSpec.IntValue WORKING_ROTATION_TICKS;
    public static final ForgeConfigSpec.IntValue WORKING_HYSTERESIS_TICKS;

    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_MOUND_TENDRIL;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_TENDRIL_PER_TASK;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_TENDRIL_GLOBAL;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_TENDRIL_MAX_JOBS;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_FOLIAGE;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_FOLIAGE_PER_TASK;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_FOLIAGE_GLOBAL;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_FOLIAGE_MAX_JOBS;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_FOLIAGE_FAST_CURSOR;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_FOLIAGE_DIRECT_CHUNK_READ;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_FOLIAGE_TIME_BUDGET;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_FOLIAGE_TIME_BUDGET_MICROS;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_TENDRIL_TIME_BUDGET;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_TENDRIL_TIME_BUDGET_MICROS;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_PATH_BACKOFF;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_PATH_MIN_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_BALANCED_TARGETING;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_TARGET_NEAR_DISTANCE;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_TARGET_NEAR_INTERVAL;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_TARGET_FAR_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_GENERAL_PATH_BACKOFF;
    public static final ForgeConfigSpec.DoubleValue AGGRESSIVE_PATH_TARGET_MOVE_THRESHOLD;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_PATH_BACKOFF_MAX;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_STATIONARY_ITEM_PHYSICS_LOD;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_STATIONARY_ITEM_INTERVAL;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_STATIONARY_ITEM_WAKE_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_ORPHAN_PROJECTILE_CLEANUP;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_ORPHAN_PROJECTILE_LIFETIME;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_REMOTE_IDLE_AI;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_REMOTE_AI_DISTANCE;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_REMOTE_AI_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_HOWITZER_CACHE;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_HOWITZER_CACHE_TICKS;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_HOWITZER_MAX_NEW_TRAJECTORIES;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_GROUP_SENSING;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_FOLLOW_SNAPSHOT_TICKS;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_FOLLOW_PATH_REUSE;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_FOLLOW_REPATH_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue AGGRESSIVE_FOLLOW_MOVE_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_FOLLOW_PATH_BACKOFF;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_FOLLOW_BACKOFF_MAX;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_SPORESRP_LAZY_HIVEMIND_QUEUE;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_SPORESRP_MINING_BUDGET;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_SPORESRP_BLOCK_GLOBAL;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_SPORESRP_SURFACE_SEARCH;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_SPORESRP_CASING_SCHEDULER;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_SPORESRP_BACKGROUND_PER_TASK;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_SPORESRP_BACKGROUND_MAX_JOBS;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_SPORESRP_PROTO_STAGGER;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_SPORESRP_FULL_HIVEMIND_STAGGER;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_SPORESRP_BUILDER_STAGGER;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_SPOREFIX_PERMANENT_AUDIT_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_SPOREFIX_PERMANENT_AUDIT;

    public static final ForgeConfigSpec.BooleanValue COMPAT_SPOREFIX_AUTO_DETECT;
    public static final ForgeConfigSpec.BooleanValue COMPAT_SPORESRP_AUTO_DETECT;
    public static final ForgeConfigSpec.BooleanValue COMPAT_TACZ_AUTO_DETECT;
    public static final ForgeConfigSpec.BooleanValue COMPAT_TACZ_BYPASS_CALAMITY_CAP;
    public static final ForgeConfigSpec.BooleanValue COMPAT_TACZ_BYPASS_SPORESRP_ADAPTATION;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMPAT_TACZ_CALAMITY_CAP_GUNS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMPAT_TACZ_ADAPTATION_BYPASS_GUNS;
    public static final ForgeConfigSpec.BooleanValue DIAGNOSTICS_METRICS;
    public static final ForgeConfigSpec.IntValue DIAGNOSTICS_SLOW_TASK_MS;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_AI_ENABLED;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_SHARED_PERCEPTION;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_EVENT_THREATS;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_GROUP_COORDINATION;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_TICK_PIPELINE;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_NAVIGATION_ENABLED;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_SHARED_CORRIDORS;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_ASYNC_LONG_PATHS;
    public static final ForgeConfigSpec.IntValue REFACTOR_ASYNC_THRESHOLD;
    public static final ForgeConfigSpec.IntValue REFACTOR_PATH_WORKERS;
    public static final ForgeConfigSpec.IntValue REFACTOR_PATH_SNAPSHOT_BUDGET;
    public static final ForgeConfigSpec.IntValue REFACTOR_PATH_RESULT_BUDGET;
    public static final ForgeConfigSpec.IntValue REFACTOR_PATH_QUEUE_LIMIT;
    public static final ForgeConfigSpec.IntValue REFACTOR_PATH_CACHE_ENTRIES;
    public static final ForgeConfigSpec.IntValue REFACTOR_PATH_CACHE_TICKS;
    public static final ForgeConfigSpec.DoubleValue REFACTOR_PATH_TARGET_MOVE_INVALIDATION;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_COMPAT_SPOREFIX;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_COMPAT_SPORESRP;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_CALAMITY_NAVIGATION_ENABLED;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_CALAMITY_SINGLE_YAW_OWNER;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_CALAMITY_PROGRESS_RECOVERY;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_CALAMITY_POSITION_PATH_CACHE;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_CALAMITY_SECTION_PATH_INVALIDATION;
    public static final ForgeConfigSpec.IntValue REFACTOR_CALAMITY_NO_PROGRESS_TICKS;
    public static final ForgeConfigSpec.IntValue REFACTOR_CALAMITY_RECOVERY_WAYPOINT_RADIUS;
    public static final ForgeConfigSpec.IntValue REFACTOR_CALAMITY_RETRY_TICKS;
    public static final ForgeConfigSpec.IntValue REFACTOR_CALAMITY_MAX_RETRY_TICKS;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_CALAMITY_EXCLUDE_VERFALLDRACHEN;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_MULTIPART_MINIMAL_TICK;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_MULTIPART_SHARED_MELEE_QUERY;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_MOUND_MINIMAL_TICK;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_ROOTED_GASTGEBER_MINIMAL_TICK;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_ENFORCE_WORK_TOKENS_BEFORE_AI;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_FOLLOW_GROUP_PATHING;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_FOLLOW_SIZE_AWARE_ARRIVAL;
    public static final ForgeConfigSpec.IntValue REFACTOR_FOLLOW_DIRECT_STEERING_DISTANCE;
    public static final ForgeConfigSpec.IntValue REFACTOR_FOLLOW_MAX_LOCAL_PATHS_PER_GROUP_TICK;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_BUSSER_ENABLED;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_BUSSER_VARIANT_GOAL_PRUNING;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_BUSSER_SHARED_AIR_SWEEP_CONTEXT;
    public static final ForgeConfigSpec.IntValue REFACTOR_BUSSER_SHORTCUT_CANDIDATES_PER_TICK;
    public static final ForgeConfigSpec.IntValue REFACTOR_BUSSER_SHORTCUT_REFRESH_TICKS;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_FOLIAGE_LAZY_NEIGHBOR_READS;
    public static final ForgeConfigSpec.BooleanValue REFACTOR_FOLIAGE_COMPILED_ACTION_PLANS;
    public static final ForgeConfigSpec.IntValue REFACTOR_FOLIAGE_TIME_BUDGET_MICROS;
    public static final ForgeConfigSpec.IntValue REFACTOR_TENDRIL_TIME_BUDGET_MICROS;
    public static final ForgeConfigSpec.BooleanValue COMPAT_TOUHOU_POWER_POINT_OPTIMIZATION;
    public static final ForgeConfigSpec.IntValue COMPAT_TOUHOU_GROUNDED_PHYSICS_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue DIAGNOSTICS_AI_REFACTOR_METRICS;
    public static final ForgeConfigSpec.BooleanValue DIAGNOSTICS_AI_SHADOW;
    public static final ForgeConfigSpec.IntValue DIAGNOSTICS_AI_SLOW_ENTITY_MICROS;
    public static final ForgeConfigSpec.BooleanValue DEBUG_ENABLED;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LIFECYCLE;
    public static final ForgeConfigSpec.BooleanValue DEBUG_PERCEPTION;
    public static final ForgeConfigSpec.BooleanValue DEBUG_GROUPS;
    public static final ForgeConfigSpec.BooleanValue DEBUG_NAVIGATION;
    public static final ForgeConfigSpec.BooleanValue DEBUG_GOALS;
    public static final ForgeConfigSpec.BooleanValue DEBUG_COMBAT;
    public static final ForgeConfigSpec.BooleanValue DEBUG_STAHL;
    public static final ForgeConfigSpec.BooleanValue DEBUG_BACKGROUND;
    public static final ForgeConfigSpec.BooleanValue DEBUG_COMPAT;
    public static final ForgeConfigSpec.BooleanValue DEBUG_INCLUDE_COORDINATES;
    public static final ForgeConfigSpec.IntValue DEBUG_SAMPLE_EVERY_N;
    public static final ForgeConfigSpec.IntValue DEBUG_MAX_EVENTS_PER_SECOND;
    public static final ForgeConfigSpec.IntValue DEBUG_RING_ENTRIES;
    public static final ForgeConfigSpec.BooleanValue CALAMITY_TRACE_ENABLED;
    public static final ForgeConfigSpec.IntValue CALAMITY_TRACE_RADIUS;
    public static final ForgeConfigSpec.IntValue CALAMITY_TRACE_MAX_TRACKED;
    public static final ForgeConfigSpec.IntValue CALAMITY_TRACE_SAMPLE_INTERVAL;
    public static final ForgeConfigSpec.IntValue CALAMITY_TRACE_MAX_EVENTS_PER_SECOND;
    public static final ForgeConfigSpec.BooleanValue CALAMITY_TRACE_INCLUDE_COORDINATES;

    static {
        ForgeConfigSpec.Builder common = new ForgeConfigSpec.Builder();
        common.push("safe");
        SAFE_SPAWNER_SERVER_ONLY = common.comment("感染刷怪笼的供养扫描只在服务端运行；建议保持开启。").define("spawnerServerOnly", true);
        SAFE_COMPILED_INFECTION_MAP = common.comment("配置加载或重载时预编译方块感染映射，移除热路径中的字符串解析；建议保持开启。").define("compiledInfectionMap", true);
        SAFE_TENDRIL_SPREAD_FAST_PATH = common.comment("优化感染卷须每 10 Tick 的局部传播，不改变触发节奏、概率或交互结果；建议保持开启。").define("tendrilSpreadFastPath", true);
        SAFE_SAME_TICK_PATH_GATE = common.comment("同一灾厄实体在同一游戏 Tick 最多执行一次路径重算；建议保持开启。").define("sameTickPathGate", true);
        SAFE_SKIP_NON_EVOLVING_CALAMITY_FOLLOW = common.comment("跳过非进化感染体必定无结果的灾厄伙伴搜索；建议保持开启。").define("skipNonEvolvingCalamityFollow", true);
        SAFE_HOWITZER_SAME_TICK_CACHE = common.comment("复用 AI Fix 的 Howitzer 在同 Tick、同目标位置的视线判断；建议保持开启。").define("howitzerSameTickCache", true);
        SAFE_SPORESRP_DIMENSION_GUARDS = common.comment("sporesrp 按维度分桶并跳过无效跨维 UUID 查找；建议保持开启。").define("sporesrpDimensionGuards", true);
        SAFE_SPORESRP_DISABLED_SHORT_CIRCUIT = common.comment("sporesrp 对已关闭的 Handler 立即返回，不建立无用缓存；建议保持开启。").define("sporesrpDisabledShortCircuit", true);
        SAFE_SONA_CAN_CHUNK_CACHE = common.comment("Sona 的区块感染总开关判定在同一维度同一 Tick 内复用；不缓存感染数值，也不改变判定结果。")
                .define("sonaCanChunkInfectionTickCache", true);
        SAFE_SPORE_PROJECTILE_BROADPHASE = common.comment("同 Tick同区块的 Spore 投射物共享一次实体碰撞候选查询，最终命中仍逐投射物精确判定。")
                .define("sporeProjectileBroadphaseCache", true);
        SAFE_PERSIST_BILE_PROJECTILE_LIFETIME = common.comment("保存 Spore 胆汁投射物已经存活的 Tick，防止区块卸载后寿命重新从零开始并遗留远距投射物；建议保持开启。")
                .define("persistBileProjectileLifetime", true);
        SAFE_BILE_PROJECTILE_LIFETIME_TICKS = common.comment("Spore 胆汁投射物的总生存时间（Tick）；300 与本体原始设定一致。")
                .defineInRange("bileProjectileLifetimeTicks", 300, 1, 72000);
        common.pop();

        common.push("refactor");
        common.push("ai");
        REFACTOR_AI_ENABLED = common.comment("启用 Spore 实体 AI 底层重构总开关；关闭后所有公共 AI 入口回退 Spore 原逻辑。")
                .define("enabled", true);
        REFACTOR_SHARED_PERCEPTION = common.comment("按维度和空间单元共享目标候选感知帧，并为每只实体重新执行原目标条件。")
                .define("sharedPerception", true);
        REFACTOR_EVENT_THREATS = common.comment("用事件驱动威胁传播替代每只感染体各自扫描附近同类。")
                .define("eventDrivenThreats", true);
        REFACTOR_GROUP_COORDINATION = common.comment("用维度级群组成员表处理目标传播和伙伴选择。")
                .define("groupCoordination", true);
        REFACTOR_TICK_PIPELINE = common.comment("启用即时、感知、导航和后台四阶段 Tick 诊断与过载保护。")
                .define("tickPipeline", true);
        common.pop();
        common.push("navigation");
        REFACTOR_NAVIGATION_ENABLED = common.comment("启用 Spore 共享导航服务；移动、碰撞和攻击仍逐 Tick。")
                .define("enabled", true);
        REFACTOR_SHARED_CORRIDORS = common.comment("允许相近体型、起点和目标区段的 Spore 单位共享路径走廊。")
                .define("sharedCorridors", true);
        REFACTOR_ASYNC_LONG_PATHS = common.comment("长距离路径在主线程复制只读快照后交给后台线程计算粗粒度走廊。")
                .define("asyncLongPaths", true);
        REFACTOR_ASYNC_THRESHOLD = common.comment("超过此距离才创建异步长路径任务（格）。")
                .defineInRange("asyncThreshold", 12, 8, 128);
        REFACTOR_PATH_WORKERS = common.comment("异步路径工作线程数量；线程只允许读取不可变快照。")
                .defineInRange("workerThreads", 2, 1, 4);
        REFACTOR_PATH_SNAPSHOT_BUDGET = common.comment("每个维度每 Tick 最多复制的路径快照数量。")
                .defineInRange("snapshotBudgetPerTick", 32, 1, 256);
        REFACTOR_PATH_RESULT_BUDGET = common.comment("每个维度每 Tick 最多接收并缓存的异步路径结果数量。")
                .defineInRange("resultBudgetPerTick", 64, 1, 512);
        REFACTOR_PATH_QUEUE_LIMIT = common.comment("单个维度允许等待的异步路径请求上限。")
                .defineInRange("queueLimit", 256, 16, 4096);
        REFACTOR_PATH_CACHE_ENTRIES = common.comment("单个维度最多缓存的共享路径走廊数量。")
                .defineInRange("cacheEntries", 2048, 64, 32768);
        REFACTOR_PATH_CACHE_TICKS = common.comment("共享路径走廊最大有效时间（Tick）；地形变化可提前失效。")
                .defineInRange("cacheTicks", 40, 1, 400);
        REFACTOR_PATH_TARGET_MOVE_INVALIDATION = common.comment("目标移动超过此距离后重新请求路径（格）。")
                .defineInRange("targetMoveInvalidation", 2.0D, 0.5D, 16.0D);
        common.pop();
        common.push("compat");
        REFACTOR_COMPAT_SPOREFIX = common.comment("AI重构在检测到 exhuashan_sporeai_fix 时避开其灾厄专属技能覆盖。")
                .define("sporeAiFix", true);
        REFACTOR_COMPAT_SPORESRP = common.comment("AI重构为 sporesrp Proto 和器官体启用软兼容适配。")
                .define("sporesrp", true);
        common.pop();
        common.push("calamityNavigation");
        REFACTOR_CALAMITY_NAVIGATION_ENABLED = common.comment("启用全灾厄导航运行时；仅调度寻路和朝向，不替换攻击、技能或移动物理。")
                .define("enabled", true);
        REFACTOR_CALAMITY_SINGLE_YAW_OWNER = common.comment("非技能状态下只允许移动执行器写入灾厄身体朝向，观察控制仅处理头部、视线和俯仰。")
                .define("singleYawOwner", true);
        REFACTOR_CALAMITY_PROGRESS_RECOVERY = common.comment("检测路径节点无进展并按重算、替代落点和退避顺序恢复，避免原地持续转圈。")
                .define("progressRecovery", true);
        REFACTOR_CALAMITY_POSITION_PATH_CACHE = common.comment("缓存灾厄前往固定 SearchArea 的坐标路径；实体目标和坐标目标使用独立缓存键。")
                .define("positionPathCache", true);
        REFACTOR_CALAMITY_SECTION_PATH_INVALIDATION = common.comment("方块变更只使经过该区段的路径失效，不再让整维度全部路径缓存失效。")
                .define("sectionPathInvalidation", true);
        REFACTOR_CALAMITY_NO_PROGRESS_TICKS = common.comment("路径节点连续未推进且位移过低多少 Tick 后判定为卡路。")
                .defineInRange("noProgressTicks", 20, 5, 200);
        REFACTOR_CALAMITY_RECOVERY_WAYPOINT_RADIUS = common.comment("第二次卡路时为固定目标选择替代落点的最大半径（格）。")
                .defineInRange("recoveryWaypointRadius", 4, 1, 16);
        REFACTOR_CALAMITY_RETRY_TICKS = common.comment("首次导航退避时间（Tick）；后续按 20、40、80 Tick 递增。")
                .defineInRange("retryTicks", 20, 1, 400);
        REFACTOR_CALAMITY_MAX_RETRY_TICKS = common.comment("灾厄导航退避的最大时间（Tick）。")
                .defineInRange("maxRetryTicks", 80, 20, 1200);
        REFACTOR_CALAMITY_EXCLUDE_VERFALLDRACHEN = common.comment("完全排除朽翼魔龙（Verfalldrachen），不改变其盘旋、寻路或技能逻辑。")
                .define("excludeVerfalldrachen", true);
        common.pop();
        common.push("multipart");
        REFACTOR_MULTIPART_MINIMAL_TICK = common.comment("为 HohlMultipart 使用轻量服务端 Tick；保留命中箱、伤害转发、父实体同步和死亡逻辑。")
                .define("minimalServerTick", true);
        REFACTOR_MULTIPART_SHARED_MELEE_QUERY = common.comment("让 HohlMultipart 近战分体共享已加载实体索引；最终范围、阵营和伤害判定仍逐分体执行。")
                .define("sharedMeleeQuery", true);
        common.pop();
        common.push("staticEntities");
        REFACTOR_MOUND_MINIMAL_TICK = common.comment("菌丘没有目标或受击时跳过移动和完整 AI，只保留年龄、感染周期和同步。")
                .define("moundMinimalTick", true);
        REFACTOR_ROOTED_GASTGEBER_MINIMAL_TICK = common.comment("扎根且未战斗的 GastGeber 跳过移动和无效 AI；脱根、受击或获得目标立即恢复。")
                .define("rootedGastgeberMinimalTick", true);
        REFACTOR_ENFORCE_WORK_TOKENS_BEFORE_AI = common.comment("在实体进入服务端 AI 前检查工作令牌，避免未取得后台名额的旧实体运行完整 Goal。")
                .define("enforceWorkTokensBeforeAi", true);
        common.pop();
        common.push("follow");
        REFACTOR_FOLLOW_GROUP_PATHING = common.comment("用维度级伙伴群组和共享走廊替代每个感染体重复创建跟随路径。")
                .define("groupPathing", true);
        REFACTOR_FOLLOW_SIZE_AWARE_ARRIVAL = common.comment("伙伴跟随抵达半径按双方碰撞箱计算，避免大型单位在近距离反复寻路。")
                .define("sizeAwareArrival", true);
        REFACTOR_FOLLOW_DIRECT_STEERING_DISTANCE = common.comment("伙伴距离不超过此值且局部路线安全时直接移动，不创建 Path。")
                .defineInRange("directSteeringDistance", 12, 2, 64);
        REFACTOR_FOLLOW_MAX_LOCAL_PATHS_PER_GROUP_TICK = common.comment("每个伙伴群组每 Tick最多创建的本地接入路径数。")
                .defineInRange("maxLocalPathsPerGroupTick", 1, 0, 16);
        common.pop();
        common.push("busser");
        REFACTOR_BUSSER_ENABLED = common.comment("启用 Busser 变体行动仲裁和飞行导航碰撞缓存。")
                .define("enabled", true);
        REFACTOR_BUSSER_VARIANT_GOAL_PRUNING = common.comment("按 Busser 变体只运行匹配的 Goal，减少互斥 Goal 的重复 canUse。")
                .define("variantGoalPruning", true);
        REFACTOR_BUSSER_SHARED_AIR_SWEEP_CONTEXT = common.comment("同一飞行导航 Tick复用碰撞区域和方块状态，避免重复创建 PathNavigationRegion。")
                .define("sharedAirSweepContext", true);
        REFACTOR_BUSSER_SHORTCUT_CANDIDATES_PER_TICK = common.comment("Busser 飞行导航每 Tick最多测试的捷径候选数量。")
                .defineInRange("shortcutCandidatesPerTick", 3, 1, 16);
        REFACTOR_BUSSER_SHORTCUT_REFRESH_TICKS = common.comment("Busser 飞行捷径碰撞结果的最短刷新间隔。")
                .defineInRange("shortcutRefreshTicks", 2, 1, 20);
        common.pop();
        common.push("foliage");
        REFACTOR_FOLIAGE_LAZY_NEIGHBOR_READS = common.comment("菌丘侵蚀只读取实际触发分支需要的邻居方块。")
                .define("lazyNeighborReads", true);
        REFACTOR_FOLIAGE_COMPILED_ACTION_PLANS = common.comment("预编译菌丘感染映射、标签和方块属性处理计划。")
                .define("compiledActionPlans", true);
        REFACTOR_FOLIAGE_TIME_BUDGET_MICROS = common.comment("菌丘侵蚀重构的全局 Tick耗时预算（微秒）。")
                .defineInRange("foliageTimeBudgetMicros", 750, 100, 50000);
        REFACTOR_TENDRIL_TIME_BUDGET_MICROS = common.comment("感染卷须重构的全局 Tick耗时预算（微秒）。")
                .defineInRange("tendrilTimeBudgetMicros", 350, 100, 50000);
        common.pop();
        common.pop();

        common.push("limits");
        LIMIT_FUNGAL_UNITS_PER_DIMENSION = common.comment("单个维度内已加载的全部 Spore 生物单位上限。0 为不限；已有存档实体永远允许读档。")
                .defineInRange("maxFungalUnitsPerDimension", 200, 0, 100000);
        LIMIT_MOUNDS_PER_DIMENSION = common.comment("单个维度内已加载的 spore:mound（菌囊/菌丘）上限。0 为不限。")
                .defineInRange("maxMoundsPerDimension", 16, 0, 100000);
        LIMIT_TENDRILS_PER_DIMENSION = common.comment("单个维度内已加载的 spore:tendril（感染卷须）上限。0 为不限；Boss 的 arena_tendril 不计入此项。")
                .defineInRange("maxTendrilsPerDimension", 32, 0, 100000);
        common.pop();

        common.push("limits");
        common.push("calamity");
        LIMIT_CALAMITY_TOTAL_PER_DIMENSION = common.comment("单个维度内所有灾厄实体总数上限；-1 表示不限制；存档已有灾厄仍允许加载。")
                .defineInRange("maxTotal", -1, -1, 100000);
        LIMIT_CALAMITY_PER_TYPE_PER_DIMENSION = common.comment("单个维度内同一种灾厄实体上限；-1 表示不限制；存档已有灾厄仍允许加载。")
                .defineInRange("maxPerType", -1, -1, 100000);
        common.pop();
        common.pop();

        common.push("items");
        common.push("merge");
        ITEM_MERGE_ENABLED = common.comment("启用 SporePerformance 自带的 Spore 掉落物区块分桶合并；Harium/Lithium 已提供合并优化时建议保持关闭。")
                .define("enabled", false);
        ITEM_MERGE_GLOBAL = common.comment("把合并协调器应用到所有模组物品；默认关闭，开启前需确认自动化兼容。")
                .define("global", false);
        ITEM_MERGE_RADIUS = common.comment("受管物品合并半径（格）。")
                .defineInRange("radius", 4, 1, 16);
        ITEM_MERGE_INTERVAL = common.comment("物品合并协调器运行间隔（Tick）。")
                .defineInRange("intervalTicks", 10, 1, 200);
        ITEM_MERGE_ENTITY_BUDGET = common.comment("单个维度每轮最多检查的物品实体数。")
                .defineInRange("entityBudget", 256, 16, 100000);
        ITEM_MERGE_TIME_BUDGET_MICROS = common.comment("单个维度每轮物品合并的耗时预算（微秒）。")
                .defineInRange("timeBudgetMicros", 750, 100, 50000);
        common.pop();
        common.push("lifetime");
        ITEM_LIFETIME_ENABLED = common.comment("缩短受管 Spore 普通掉落物的自然消失时间；玩家和特殊物品受保护。")
                .define("enabled", true);
        ITEM_LIFETIME_FAST = common.comment("常见 Spore 废料的自然消失时间（Tick）；1200 Tick 为 60 秒。")
                .defineInRange("fastTicks", 1200, 100, 72000);
        ITEM_LIFETIME_NORMAL = common.comment("其他 Spore 普通掉落物的自然消失时间（Tick）；2400 Tick 为 120 秒。")
                .defineInRange("normalTicks", 2400, 100, 72000);
        ITEM_LIFETIME_PLAYER = common.comment("玩家主动丢弃物品的保护时间（Tick）；6000 Tick 为原版 5 分钟。")
                .defineInRange("playerDroppedTicks", 6000, 100, 72000);
        ITEM_LIFETIME_PROTECT_SPECIAL = common.comment("保护不可堆叠、命名、附魔、损伤或带自定义 NBT 的物品。")
                .define("protectSpecialItems", true);
        common.pop();
        common.pop();

        common.push("limits");
        common.push("working");
        WORKING_FUNGAL_UNITS = common.comment("单个维度内同时执行完整服务端 AI 的 Basic/Evolved/Hyper 感染体数量；超额旧实体保留，停止感知、Goal、寻路和控制器 Tick，但物理、受击、生命和同步仍逐 Tick运行。")
                .defineInRange("maxActiveFungalUnitsPerDimension", 200, 0, 100000);
        WORKING_GASTGEBERS = common.comment("单个维度内同时执行感染传播工作的 GastGeber 数量。")
                .defineInRange("maxWorkingGastGebersPerDimension", 12, 0, 100000);
        WORKING_MOUNDS = common.comment("单个维度内同时执行感染和卷须任务的 Mound 数量。")
                .defineInRange("maxWorkingMoundsPerDimension", 8, 0, 100000);
        WORKING_TENDRILS = common.comment("单个维度内同时执行方块传播的感染卷须数量。")
                .defineInRange("maxWorkingTendrilsPerDimension", 16, 0, 100000);
        WORKING_ROTATION_TICKS = common.comment("后台工作名额重新排序和轮换的周期（Tick）。")
                .defineInRange("rotationTicks", 200, 20, 12000);
        WORKING_HYSTERESIS_TICKS = common.comment("实体获得工作名额后的最短保持时间（Tick），用于避免频繁抖动。")
                .defineInRange("hysteresisTicks", 40, 0, 1200);
        common.pop();
        common.pop();

        common.push("aggressive");
        AGGRESSIVE_MOUND_TENDRIL = common.comment("启用后将菌囊的卷须目标大范围扫描拆分到多个 Tick；会延后找到目标的时间。").define("moundTendrilScheduler", false);
        AGGRESSIVE_TENDRIL_PER_TASK = common.comment("单个菌囊卷须扫描任务每 Tick 最多检查的方块数。").defineInRange("tendrilBlocksPerTaskTick", 4096, 64, 262144);
        AGGRESSIVE_TENDRIL_GLOBAL = common.comment("所有菌囊卷须扫描任务每 Tick 共用的方块预算。").defineInRange("tendrilBlocksGlobalTick", 16384, 64, 1048576);
        AGGRESSIVE_TENDRIL_MAX_JOBS = common.comment("可同时排队的菌囊卷须扫描任务上限；满时请求会延后重试。").defineInRange("maxTendrilJobs", 64, 1, 4096);
        AGGRESSIVE_FOLIAGE = common.comment("启用后将菌囊侵蚀、菌丝和结构生成拆分到多个 Tick；会延长后台完成时间。").define("foliageScheduler", false);
        AGGRESSIVE_FOLIAGE_PER_TASK = common.comment("单个菌囊侵蚀任务每 Tick 最多处理的方块数。").defineInRange("foliageBlocksPerTaskTick", 2048, 64, 262144);
        AGGRESSIVE_FOLIAGE_GLOBAL = common.comment("所有菌囊侵蚀任务每 Tick 共用的方块预算。").defineInRange("foliageBlocksGlobalTick", 8192, 64, 1048576);
        AGGRESSIVE_FOLIAGE_MAX_JOBS = common.comment("可同时排队的菌囊侵蚀任务上限。").defineInRange("maxFoliageJobs", 128, 1, 4096);
        AGGRESSIVE_FOLIAGE_FAST_CURSOR = common.comment("使用低分配整数游标，并在读取世界前排除球体外位置；仅在 foliageScheduler 开启时生效。")
                .define("foliageFastCursor", false);
        AGGRESSIVE_FOLIAGE_DIRECT_CHUNK_READ = common.comment("按扫描中的当前区块复用已加载 LevelChunk；不会请求或加载新区块。")
                .define("foliageDirectLoadedChunkRead", false);
        AGGRESSIVE_FOLIAGE_TIME_BUDGET = common.comment("为菌囊侵蚀任务增加真实耗时上限；达到上限后保留游标并延后继续。")
                .define("foliageTimeBudget", false);
        AGGRESSIVE_FOLIAGE_TIME_BUDGET_MICROS = common.comment("全部菌囊侵蚀任务单 Tick 共用的时间预算，单位微秒。")
                .defineInRange("foliageTimeBudgetMicros", 1500, 100, 50000);
        AGGRESSIVE_TENDRIL_TIME_BUDGET = common.comment("为菌囊卷须目标搜索增加真实耗时上限；达到上限后保留游标并延后继续。")
                .define("tendrilTimeBudget", false);
        AGGRESSIVE_TENDRIL_TIME_BUDGET_MICROS = common.comment("全部菌囊卷须搜索任务单 Tick 共用的时间预算，单位微秒。")
                .defineInRange("tendrilTimeBudgetMicros", 500, 100, 50000);
        AGGRESSIVE_PATH_BACKOFF = common.comment("不可达目标时让灾厄寻路按退避间隔重试；目标移动、受击和关键技能会立刻解除退避。").define("calamityPathBackoff", false);
        AGGRESSIVE_PATH_MIN_INTERVAL = common.comment("灾厄路径重算的最小间隔（Tick）；仅在 calamityPathBackoff 开启时生效。").defineInRange("calamityPathMinIntervalTicks", 10, 1, 100);
        AGGRESSIVE_BALANCED_TARGETING = common.comment("将 Spore 无目标实体的目标获取按距离错峰到 2–5 Tick；受击和目标失效会立即刷新。")
                .define("balancedTargetAcquisition", true);
        AGGRESSIVE_TARGET_NEAR_DISTANCE = common.comment("使用近距离目标检查间隔的范围（格）。")
                .defineInRange("targetNearDistance", 16, 4, 128);
        AGGRESSIVE_TARGET_NEAR_INTERVAL = common.comment("近距离无目标实体的目标检查间隔（Tick）。")
                .defineInRange("targetNearIntervalTicks", 2, 1, 20);
        AGGRESSIVE_TARGET_FAR_INTERVAL = common.comment("远距离无目标实体的目标检查间隔（Tick）。")
                .defineInRange("targetFarIntervalTicks", 5, 1, 40);
        AGGRESSIVE_GENERAL_PATH_BACKOFF = common.comment("为普通感染体、进化体、Hyper 和器官体启用路径复用及失败退避。")
                .define("generalPathBackoff", true);
        AGGRESSIVE_PATH_TARGET_MOVE_THRESHOLD = common.comment("目标移动超过此距离时立即解除路径等待（格）。")
                .defineInRange("pathTargetMoveThreshold", 2.0D, 0.25D, 32.0D);
        AGGRESSIVE_PATH_BACKOFF_MAX = common.comment("普通 Spore 寻路失败后的最大退避时间（Tick）。")
                .defineInRange("pathBackoffMaxTicks", 80, 10, 400);
        AGGRESSIVE_STATIONARY_ITEM_PHYSICS_LOD = common.comment("降低远离玩家且静止落地物品的物理计算频率；年龄和拾取仍逐 Tick。")
                .define("stationaryItemPhysicsLod", false);
        AGGRESSIVE_STATIONARY_ITEM_INTERVAL = common.comment("静止物品执行完整物理的间隔（Tick）。")
                .defineInRange("stationaryItemPhysicsInterval", 4, 2, 20);
        AGGRESSIVE_STATIONARY_ITEM_WAKE_DISTANCE = common.comment("玩家进入此距离后恢复物品逐 Tick物理（格）。")
                .defineInRange("stationaryItemWakeDistance", 8, 2, 64);
        AGGRESSIVE_ORPHAN_PROJECTILE_CLEANUP = common.comment("清理超过寿命且所有者已失效的 Spore 投射物；默认关闭。")
                .define("orphanProjectileCleanup", false);
        AGGRESSIVE_ORPHAN_PROJECTILE_LIFETIME = common.comment("孤立 Spore 投射物允许存在的最长时间（Tick）。")
                .defineInRange("orphanProjectileLifetimeTicks", 600, 100, 72000);
        AGGRESSIVE_REMOTE_IDLE_AI = common.comment("远离玩家且空闲的 Basic/Evolved/Hyper 感染体降低 Goal/TargetSelector 更新频率。").define("remoteIdleAi", false);
        AGGRESSIVE_REMOTE_AI_DISTANCE = common.comment("距离最近玩家超过此距离才允许远距空闲 AI 降频。").defineInRange("remoteIdleAiDistance", 96, 32, 512);
        AGGRESSIVE_REMOTE_AI_INTERVAL = common.comment("远距空闲 AI 的 Goal/TargetSelector 更新间隔（Tick）；物理、生命和同步仍逐 Tick。").defineInRange("remoteIdleAiInterval", 10, 2, 40);
        AGGRESSIVE_HOWITZER_CACHE = common.comment("缓存 AI Fix Howitzer 的轨迹计算，并限制每 Tick 的新轨迹数量。").define("howitzerTrajectoryCache", false);
        AGGRESSIVE_HOWITZER_CACHE_TICKS = common.comment("Howitzer 轨迹缓存的有效 Tick 数；目标移动超过 1.5 格会提前失效。").defineInRange("howitzerTrajectoryCacheTicks", 5, 1, 20);
        AGGRESSIVE_HOWITZER_MAX_NEW_TRAJECTORIES = common.comment("每个 Howitzer 每 Tick 最多允许的新轨迹探测数；命中缓存的探测不消耗该预算。")
                .defineInRange("howitzerMaxNewTrajectoriesPerTick", 8, 1, 128);
        AGGRESSIVE_GROUP_SENSING = common.comment("同一目标的感染体群体感知共享邻居查询，并错峰伙伴跟随搜索。").define("groupSensingCache", false);
        AGGRESSIVE_FOLLOW_SNAPSHOT_TICKS = common.comment("伙伴跟随候选快照的有效期（Tick）；仅在 groupSensingCache 开启时生效。").defineInRange("followSnapshotTicks", 20, 1, 200);
        AGGRESSIVE_FOLLOW_PATH_REUSE = common.comment("伙伴没有明显移动且现有路径仍有效时复用路径，并将周期重算按 UUID 错峰。")
                .define("followPathReuse", false);
        AGGRESSIVE_FOLLOW_REPATH_INTERVAL = common.comment("伙伴跟随路径的最短周期重算间隔（Tick）；伙伴变更或明显移动会立即解除等待。")
                .defineInRange("followRepathIntervalTicks", 40, 20, 200);
        AGGRESSIVE_FOLLOW_MOVE_THRESHOLD = common.comment("伙伴移动超过此距离时允许提前重算路径。")
                .defineInRange("followRepathMoveThreshold", 2.0D, 0.5D, 16.0D);
        AGGRESSIVE_FOLLOW_PATH_BACKOFF = common.comment("伙伴寻路失败后按递增间隔重试；伙伴变更或明显移动会立即解除退避。")
                .define("followPathFailureBackoff", false);
        AGGRESSIVE_FOLLOW_BACKOFF_MAX = common.comment("伙伴寻路失败的最大退避时间（Tick）。")
                .defineInRange("followPathBackoffMaxTicks", 80, 20, 400);
        AGGRESSIVE_SPORESRP_LAZY_HIVEMIND_QUEUE = common.comment("将 sporesrp 完整心智半径 50 的预分配 BlockPos 队列替换为常量内存游标。")
                .define("sporesrpLazyFullHivemindQueue", false);
        AGGRESSIVE_SPORESRP_MINING_BUDGET = common.comment("用 sporesrp 共享方块预算限制完整心智原有采矿切片。")
                .define("sporesrpMiningBudget", false);
        AGGRESSIVE_SPORESRP_BLOCK_GLOBAL = common.comment("完整心智采矿共用的每 Tick 方块预算；被延后的切片会从原游标继续。")
                .defineInRange("sporesrpBlocksGlobalTick", 8192, 64, 1048576);
        AGGRESSIVE_SPORESRP_SURFACE_SEARCH = common.comment("将 Proto 和完整心智的地表搜索改为仅已加载区块的游标任务。")
                .define("sporesrpSurfaceSearchScheduler", false);
        AGGRESSIVE_SPORESRP_CASING_SCHEDULER = common.comment("将完整心智外壳建造改为仅已加载区块的游标任务。")
                .define("sporesrpCasingScheduler", false);
        AGGRESSIVE_SPORESRP_BACKGROUND_PER_TASK = common.comment("单个 sporesrp 地表/外壳后台任务每 Tick 最多处理的方块数。")
                .defineInRange("sporesrpBackgroundBlocksPerTaskTick", 2048, 16, 262144);
        AGGRESSIVE_SPORESRP_BACKGROUND_MAX_JOBS = common.comment("可同时排队的 sporesrp 地表和外壳任务上限。")
                .defineInRange("sporesrpBackgroundMaxJobs", 128, 1, 4096);
        AGGRESSIVE_SPORESRP_PROTO_STAGGER = common.comment("sporesrp Proto 技能检查的 UUID 错峰因子；1 保持原生节奏。")
                .defineInRange("sporesrpProtoStagger", 1, 1, 40);
        AGGRESSIVE_SPORESRP_FULL_HIVEMIND_STAGGER = common.comment("sporesrp 完整心智技能检查的 UUID 错峰因子；1 保持原生节奏。")
                .defineInRange("sporesrpFullHivemindStagger", 1, 1, 40);
        AGGRESSIVE_SPORESRP_BUILDER_STAGGER = common.comment("sporesrp Builder 检查的 UUID 错峰因子；1 保持原生节奏。")
                .defineInRange("sporesrpBuilderStagger", 1, 1, 40);
        AGGRESSIVE_SPOREFIX_PERMANENT_AUDIT_INTERVAL = common.comment("AI Fix 非关键永久实体维护的检查间隔（Tick）；伤害、死亡和移除保护仍即时。")
                .defineInRange("sporefixPermanentAuditInterval", 20, 1, 200);
        AGGRESSIVE_SPOREFIX_PERMANENT_AUDIT = common.comment("降低 AI Fix 非关键永久实体维护频率。")
                .define("sporefixPermanentAuditScheduler", false);
        common.pop();

        common.push("compat");
        common.push("sporefix");
        COMPAT_SPOREFIX_AUTO_DETECT = common.comment("自动检测 AI Fix 是否存在以及兼容签名；关闭后不启用其专属优化。").define("autoDetect", true);
        common.pop();
        common.push("sporesrp");
        COMPAT_SPORESRP_AUTO_DETECT = common.comment("自动检测 sporesrp 是否存在以及兼容签名；关闭后不启用其专属优化。").define("autoDetect", true);
        common.pop();
        common.push("tacz");
        COMPAT_TACZ_AUTO_DETECT = common.comment("自动检测 TACZ 子弹类和枪械 ID 方法；缺失或签名不匹配时只关闭本兼容项。").define("autoDetect", true);
        COMPAT_TACZ_BYPASS_CALAMITY_CAP = common.comment("允许白名单 TACZ 武器绕过 Spore 灾厄的单次受击伤害上限；其他武器仍使用原版上限。").define("bypassCalamityDamageCap", true);
        COMPAT_TACZ_BYPASS_SPORESRP_ADAPTATION = common.comment("允许白名单 TACZ 武器绕过 Spore 灾厄自身适应和 sporesrp 的适应性减伤；这是独立于单次伤害上限的开关。").define("bypassSrpAdaptation", true);
        COMPAT_TACZ_CALAMITY_CAP_GUNS = common.comment("绕过 Spore 灾厄单次伤害上限的 TACZ 枪械 ID；使用命名空间:枪械路径。")
                .defineListAllowEmpty("calamityDamageCapGunIds", java.util.List.of(
                        "cib:qlu11", "cib:dzj08", "zeta:railgun", "cib:qbu201", "cib:qbu10", "jccrossbow:compound_crossbow_data",
                        "cib:qbu202", "cib:qbu203", "create_armorer:sniper_semi_clockwork", "immersive_armorer:railgun",
                        "jccrossbow:compound_crossbow", "sfms:nitro_505", "sfms:tb23", "zeta:apw1", "zeta:r6", "cib:qjz171"),
                        value -> value instanceof String);
        COMPAT_TACZ_ADAPTATION_BYPASS_GUNS = common.comment("绕过 Spore 灾厄自身适应和 sporesrp 适应性减伤的 TACZ 枪械 ID；使用命名空间:枪械路径。")
                .defineListAllowEmpty("srpAdaptationBypassGunIds", java.util.List.of(
                        "cib:qlu11", "cib:dzj08", "zeta:railgun"),
                        value -> value instanceof String);
        common.pop();
        common.push("touhouLittleMaid");
        COMPAT_TOUHOU_POWER_POINT_OPTIMIZATION = common.comment("女仆 P 点软兼容：共享附近玩家查询并降低无人静止 P 点物理频率，不合并或改变 P 点价值。")
                .define("powerPointOptimization", true);
        COMPAT_TOUHOU_GROUNDED_PHYSICS_INTERVAL = common.comment("无人接近且落地静止 P 点的完整物理检查间隔（Tick）。")
                .defineInRange("groundedPhysicsInterval", 4, 1, 20);
        common.pop();
        common.pop();

        common.push("diagnostics");
        DIAGNOSTICS_METRICS = common.comment("收集 /sporeperformance metrics 使用的轻量计数器；默认关闭以减少额外开销。").define("metrics", false);
        DIAGNOSTICS_SLOW_TASK_MS = common.comment("慢任务日志阈值（毫秒）；供诊断记录使用。").defineInRange("slowTaskMilliseconds", 25, 1, 10000);
        common.push("aiRefactor");
        DIAGNOSTICS_AI_REFACTOR_METRICS = common.comment("收集 AI 重构的目标、LOS、群体、路径和阶段计数。")
                .define("metrics", false);
        DIAGNOSTICS_AI_SHADOW = common.comment("影子比较新旧目标与路径决策；只记录差异，不改变游戏结果。")
                .define("shadowComparison", false);
        DIAGNOSTICS_AI_SLOW_ENTITY_MICROS = common.comment("记录单实体 AI 阶段慢调用的阈值（微秒）。")
                .defineInRange("slowEntityMicros", 500, 50, 100000);
        common.pop();
        common.push("debugTrace");
        DEBUG_ENABLED = common.comment("启用独立结构化调试日志；默认关闭，测试时再开启。").define("enabled", false);
        DEBUG_LIFECYCLE = common.comment("记录实体加入、离开、维度卸载及调试模块生命周期。").define("entityLifecycle", true);
        DEBUG_PERCEPTION = common.comment("记录共享感知查询、候选数量、缓存命中和最终目标。").define("perception", true);
        DEBUG_GROUPS = common.comment("记录威胁传播、群组广播、领队和伙伴分配。").define("threatsAndGroups", true);
        DEBUG_NAVIGATION = common.comment("记录路径缓存、队列、快照、异步结果、失效和回退。").define("navigation", true);
        DEBUG_GOALS = common.comment("记录关键Goal启动、继续、停止及拒绝原因。").define("goals", true);
        DEBUG_COMBAT = common.comment("记录攻击准备、最终复核、命中和范围攻击候选。").define("combat", true);
        DEBUG_STAHL = common.comment("记录Stahl跳跃、空中状态、落地伤害及特效实体。").define("stahl", true);
        DEBUG_BACKGROUND = common.comment("记录菌丘、卷须、GastGeber、工作令牌和后台队列。").define("backgroundWork", true);
        DEBUG_COMPAT = common.comment("记录AI Fix、sporesrp及Mixin委托/兼容状态。").define("compatibility", true);
        DEBUG_INCLUDE_COORDINATES = common.comment("在调试记录中写入实体坐标。").define("includeCoordinates", true);
        DEBUG_SAMPLE_EVERY_N = common.comment("普通实体事件每N Tick/实体组合采样一次；1表示全部记录。")
                .defineInRange("sampleEveryN", 1, 1, 1000);
        DEBUG_MAX_EVENTS_PER_SECOND = common.comment("每秒最多写入的普通调试事件数；严重错误不受此限制。")
                .defineInRange("maxEventsPerSecond", 500, 10, 100000);
        DEBUG_RING_ENTRIES = common.comment("内存中保留的最近调试事件数量，供管理员命令查看。")
                .defineInRange("ringEntries", 1024, 64, 32768);
        common.pop();
        common.push("calamityTrace");
        CALAMITY_TRACE_ENABLED = common.comment("启用只记录附近灾厄生物的独立追踪日志；不会混入普通实体调试日志。")
                .define("enabled", false);
        CALAMITY_TRACE_RADIUS = common.comment("距离任一服务器玩家不超过此范围的灾厄才会被追踪（格）。")
                .defineInRange("radius", 128, 16, 512);
        CALAMITY_TRACE_MAX_TRACKED = common.comment("同一时间允许写入详细状态的附近灾厄最大数量，防止大规模战斗刷满日志。")
                .defineInRange("maxTracked", 12, 1, 128);
        CALAMITY_TRACE_SAMPLE_INTERVAL = common.comment("每只被追踪灾厄记录完整决策快照的间隔（Tick）；路径和卡住事件仍立即记录。")
                .defineInRange("sampleIntervalTicks", 5, 1, 200);
        CALAMITY_TRACE_MAX_EVENTS_PER_SECOND = common.comment("每秒最多写入的灾厄追踪事件数；超过时丢弃普通追踪事件而不阻塞服务器。")
                .defineInRange("maxEventsPerSecond", 240, 10, 100000);
        CALAMITY_TRACE_INCLUDE_COORDINATES = common.comment("在灾厄追踪记录中写入坐标、速度和身体/头部朝向。")
                .define("includeCoordinates", true);
        common.pop();
        common.pop();
        COMMON_SPEC = common.build();

        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        client.push("safe");
        CLIENT_HINDERBURG_INDEX = client.comment("客户端用实体索引替代 AI Fix Hinderburg 的渲染世界遍历；建议保持开启。").define("hinderburgIndex", true);
        client.push("sporeRendering");
        CLIENT_DEFER_ILLUSION_ENTITY_CREATION = client.comment("仅在疯狂等级、观察距离等幻觉条件满足后才创建伪装实体；建议保持开启。")
                .define("deferIllusionEntityCreation", true);
        CLIENT_CACHE_ILLUSION_ENTITY_TYPES = client.comment("缓存感染体来源字符串对应的实体类型，避免渲染时重复解析注册表；建议保持开启。")
                .define("cacheIllusionEntityTypes", true);
        CLIENT_SKIP_DUPLICATE_LAYER_ANIMATION = client.comment("额外膜层重复使用父模型同一姿势时跳过第二次动画计算；建议保持开启。")
                .define("skipDuplicateLayerAnimation", true);
        CLIENT_FIX_SONA_INFECTION_POST_DEPTH = client.comment("修复 Sona 感染后处理在 Oculus 下逐帧复制不兼容深度格式的问题；改为直接采样主深度纹理，画面语义不变。")
                .define("fixSonaInfectionPostDepthCopy", true);
        client.pop();
        client.push("sonaRendering");
        CLIENT_SONA_SHARE_FRAME_SAMPLE = client.comment("Sona 覆盖层和感染后处理在同一渲染帧共享感染度与雾色采样；建议保持开启。")
                .define("shareInfectionFrameSample", true);
        CLIENT_SONA_BATCH_OVERLAY_QUADS = client.comment("把 Sona 最多 108 个孢子 GUI 方块合并为一次顶点提交；画面位置、颜色和顺序不变。")
                .define("batchSporeOverlayQuads", true);
        CLIENT_SONA_PRECOMPUTE_OVERLAY_SEEDS = client.comment("预计算 Sona 孢子粒子不随帧变化的哈希种子、尺寸和速度参数。")
                .define("precomputeSporeOverlaySeeds", true);
        client.pop();
        client.pop();
        client.push("aggressive");
        client.push("sporeRendering");
        client.push("layers");
        CLIENT_EYE_DISTANCE_CULL = client.comment("超过设定距离后不渲染感染体额外眼睛发光层；默认关闭，可能减少远处发光细节。")
                .define("eyeDistanceCull", false);
        CLIENT_EYE_RENDER_DISTANCE = client.comment("普通感染体眼睛发光层的最远显示距离（格）。")
                .defineInRange("eyeRenderDistance", 64, 8, 512);
        CLIENT_TRANSLUCENT_DISTANCE_CULL = client.comment("超过设定距离后不渲染膜、液体、变异血管和水下伪装等透明层；默认关闭。")
                .define("translucentDistanceCull", false);
        CLIENT_TRANSLUCENT_RENDER_DISTANCE = client.comment("普通感染体透明效果层的最远显示距离（格）。")
                .defineInRange("translucentRenderDistance", 48, 8, 512);
        CLIENT_EMISSIVE_DISTANCE_CULL = client.comment("超过设定距离后不渲染 Howitzer、Hindenburg 等额外灯光层；默认关闭。")
                .define("emissiveDistanceCull", false);
        CLIENT_EMISSIVE_RENDER_DISTANCE = client.comment("普通感染体额外灯光层的最远显示距离（格）。")
                .defineInRange("emissiveRenderDistance", 64, 8, 512);
        CLIENT_MAJOR_EFFECT_DISTANCE = client.comment("灾厄、Hyper、器官体和 Proto 等大型单位额外效果层的统一显示距离（格）。")
                .defineInRange("majorEntityEffectDistance", 96, 8, 1024);
        CLIENT_CALAMITY_EFFECT_CULL = client.comment("单独允许灾厄生物的额外效果层按距离剔除；默认关闭。")
                .define("calamityEffectCull", false);
        CLIENT_CALAMITY_EFFECT_DISTANCE = client.comment("灾厄生物额外效果层的最远显示距离（格）。")
                .defineInRange("calamityEffectDistance", 96, 8, 2048);
        CLIENT_ORGANOID_EFFECT_CULL = client.comment("单独允许器官体的额外效果层按距离剔除；默认关闭。")
                .define("organoidEffectCull", false);
        CLIENT_ORGANOID_EFFECT_DISTANCE = client.comment("器官体额外效果层的最远显示距离（格）。")
                .defineInRange("organoidEffectDistance", 96, 8, 2048);
        CLIENT_HYPER_EFFECT_CULL = client.comment("单独允许 Hyper 感染体的额外效果层按距离剔除；默认关闭。")
                .define("hyperEffectCull", false);
        CLIENT_HYPER_EFFECT_DISTANCE = client.comment("Hyper 感染体额外效果层的最远显示距离（格）。")
                .defineInRange("hyperEffectDistance", 96, 8, 2048);
        CLIENT_PROTO_EFFECT_CULL = client.comment("单独允许 Proto 的额外效果层按距离剔除；默认关闭。")
                .define("protoEffectCull", false);
        CLIENT_PROTO_EFFECT_DISTANCE = client.comment("Proto 额外效果层的最远显示距离（格）。")
                .defineInRange("protoEffectDistance", 96, 8, 2048);
        CLIENT_VERIFIED_MULTI_ROOT_PART_MASK = client.comment("允许安装版签名已验证的多根模型使用 Alpha/UV 部件裁剪；默认关闭。")
                .define("verifiedMultiRootPartMask", false);
        CLIENT_EYE_OPAQUE_PART_MASK = client.comment("只提交眼睛贴图中实际含不透明像素的模型部件；默认关闭，解析失败时回退整模。")
                .define("eyeOpaquePartMask", false);
        CLIENT_EMISSIVE_OPAQUE_PART_MASK = client.comment("只提交灯光贴图中实际含不透明像素的模型部件；默认关闭，解析失败时回退整模。")
                .define("emissiveOpaquePartMask", false);
        client.pop();
        client.push("animation");
        CLIENT_ANIMATION_LOD = client.comment("降低远处普通 Spore 生物模型动画计算频率；只影响客户端姿势刷新，默认关闭。")
                .define("animationLod", false);
        CLIENT_ANIMATION_NEAR_DISTANCE = client.comment("普通 Spore 生物保持逐帧动画的近距离范围（格）。")
                .defineInRange("normalNearDistance", 32, 4, 512);
        CLIENT_ANIMATION_MEDIUM_DISTANCE = client.comment("普通 Spore 生物动画中距离档的上限（格）。")
                .defineInRange("normalMediumDistance", 64, 8, 768);
        CLIENT_ANIMATION_FAR_DISTANCE = client.comment("普通 Spore 生物动画远距离档的上限（格）；更远使用最慢档。")
                .defineInRange("normalFarDistance", 96, 16, 1024);
        CLIENT_ANIMATION_MEDIUM_INTERVAL = client.comment("普通 Spore 生物处于中距离档时每隔多少帧重新计算动画。")
                .defineInRange("mediumIntervalFrames", 2, 1, 40);
        CLIENT_ANIMATION_FAR_INTERVAL = client.comment("普通 Spore 生物处于远距离档时每隔多少帧重新计算动画。")
                .defineInRange("farIntervalFrames", 4, 1, 80);
        CLIENT_ANIMATION_VERY_FAR_INTERVAL = client.comment("普通 Spore 生物超过远距离档后每隔多少帧重新计算动画。")
                .defineInRange("veryFarIntervalFrames", 8, 1, 160);
        CLIENT_MAJOR_ANIMATION_LOD = client.comment("允许灾厄、Hyper、器官体和 Proto 使用动画降频；默认关闭以保证大型单位技能表现。")
                .define("majorEntityAnimationLod", false);
        CLIENT_CALAMITY_ANIMATION_LOD = client.comment("单独允许灾厄生物使用大型单位动画降频；默认关闭。")
                .define("calamityAnimationLod", false);
        CLIENT_ORGANOID_ANIMATION_LOD = client.comment("单独允许器官体使用大型单位动画降频；默认关闭。")
                .define("organoidAnimationLod", false);
        CLIENT_HYPER_ANIMATION_LOD = client.comment("单独允许 Hyper 感染体使用大型单位动画降频；默认关闭。")
                .define("hyperAnimationLod", false);
        CLIENT_PROTO_ANIMATION_LOD = client.comment("单独允许 Proto 使用大型单位动画降频；默认关闭。")
                .define("protoAnimationLod", false);
        CLIENT_MAJOR_ANIMATION_NEAR_DISTANCE = client.comment("大型单位保持逐帧动画的近距离范围（格）。")
                .defineInRange("majorNearDistance", 64, 8, 1024);
        CLIENT_MAJOR_ANIMATION_FAR_DISTANCE = client.comment("大型单位动画降频的远距离参考范围（格）。")
                .defineInRange("majorFarDistance", 128, 16, 2048);
        CLIENT_MAJOR_ANIMATION_FAR_INTERVAL = client.comment("大型单位超过近距离范围后每隔多少帧重新计算动画。")
                .defineInRange("majorFarIntervalFrames", 4, 1, 80);
        CLIENT_POSE_CACHE_MAX_ENTITIES = client.comment("动画降频最多保存多少个实体与模型组合的姿势；超出时回退逐帧计算。")
                .defineInRange("poseCacheMaxEntities", 512, 16, 8192);
        client.pop();
        client.pop();
        client.push("sonaRendering");
        CLIENT_SONA_OVERLAY_GEOMETRY_LOD = client.comment("降低 Sona 屏幕孢子覆盖层顶点更新频率；其余帧复用 GPU 顶点缓存，默认关闭。")
                .define("overlayGeometryLod", false);
        CLIENT_SONA_OVERLAY_UPDATE_INTERVAL = client.comment("Sona 屏幕孢子覆盖层每隔多少帧重建一次顶点；仅在 overlayGeometryLod 开启时生效。")
                .defineInRange("overlayUpdateIntervalFrames", 2, 2, 4);
        CLIENT_SONA_OVERLAY_PARTICLE_SCALE_ENABLED = client.comment("允许按比例减少 Sona 屏幕孢子数量；默认关闭。")
                .define("overlayParticleScaleEnabled", false);
        CLIENT_SONA_OVERLAY_PARTICLE_SCALE = client.comment("Sona 屏幕孢子数量比例；仅在 overlayParticleScaleEnabled 开启时生效。")
                .defineInRange("overlayParticleScale", 1.0D, 0.1D, 1.0D);
        CLIENT_SONA_POST_HALF_RESOLUTION = client.comment("让 Sona 感染后处理使用 0.5 倍分辨率的颜色中间目标；主深度仍保持全分辨率，默认关闭。")
                .define("postHalfResolution", false);
        client.pop();
        client.pop();
        client.push("localRendering");
        CLIENT_FUNGAL_DECORATION_DISTANCE_CULL = client.comment("只渲染观察点附近的高密度真菌装饰方块；需要 Embeddium，默认关闭。启用前请关闭全隐藏诊断资源包。")
                .define("fungalDecorationDistanceCull", false);
        CLIENT_FUNGAL_DECORATION_DISTANCE = client.comment("真菌装饰方块模型的显示距离（格）；方块状态、碰撞、光照和服务端逻辑不受影响。")
                .defineInRange("fungalDecorationRenderDistance", 32, 8, 256);
        CLIENT_FUNGAL_DECORATION_COMMAND_DISTANCE = client.comment("指挥模式使用实际摄像机为中心时的真菌装饰显示距离（格）；高空镜头默认扩大到128格。")
                .defineInRange("fungalDecorationCommandCameraRenderDistance", 128, 8, 512);
        CLIENT_FUNGAL_DECORATION_CAMERA_STEP = client.comment("观察点移动多少格后更新一次剔除边界；越小边界越及时，但区段重建更频繁。")
                .defineInRange("fungalDecorationCameraStep", 2, 1, 16);
        CLIENT_FUNGAL_DECORATION_REBUILDS_PER_TICK = client.comment("每个客户端 Tick 最多请求重建多少个含目标方块的区段；越大更新越快，但瞬时压力越高。")
                .defineInRange("fungalDecorationSectionRebuildsPerTick", 8, 1, 64);
        client.pop();
        client.push("compat");
        client.push("acceleratedRendering");
        CLIENT_ACCELERATED_RENDERING_AUTO_DETECT = client.comment("自动检测 AcceleratedRendering 及所需方法签名；缺失或不兼容时安全跳过。")
                .define("autoDetect", true);
        CLIENT_ACCELERATE_EMISSIVE_LAYERS = client.comment("临时强制 Spore 眼睛和灯光层使用 AcceleratedRendering 管线；默认关闭，需重启完成签名检测。")
                .define("accelerateEmissiveLayers", false);
        CLIENT_ACCELERATE_TRANSLUCENT_LAYERS = client.comment("临时强制 Spore 透明膜和血管层使用 AcceleratedRendering 管线；默认关闭，需重启完成签名检测。")
                .define("accelerateTranslucentLayers", false);
        client.pop();
        client.push("sporesrp");
        CLIENT_SPORESRP_HUD_HOTBAR = client.comment("接管 sporesrp HUD，取消原实现在每个 Overlay 阶段的重复绘制；建议保持开启。")
                .define("relocateHudToHotbar", true);
        CLIENT_SPORESRP_HUD_ABOVE_SCREENS = client.comment("打开背包或其他界面时，在屏幕最前层重新绘制 sporesrp HUD，避免被背景模糊处理；仅在 relocateHudToHotbar 开启时生效。")
                .define("renderHudAboveScreens", true);
        CLIENT_SPORESRP_HUD_IN_GAMEPLAY = client.comment("普通游戏画面是否绘制 sporesrp HUD；默认关闭，关闭时仅在背包、箱子或其他 Screen 打开后显示。")
                .define("renderHudInGameplay", false);
        client.pop();
        client.pop();
        client.push("diagnostics");
        client.push("clientRendering");
        CLIENT_RENDER_METRICS = client.comment("收集 /sporeperformanceclient metrics 使用的客户端渲染计数器；默认关闭以减少额外开销。")
                .define("renderMetrics", false);
        client.pop();
        client.pop();
        CLIENT_SPEC = client.build();
    }

    private PerformanceConfig() {}
}
