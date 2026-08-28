# SporePerformance 1.0.0 全面发布审计（2026-08-24）

## 审计结论

**当前 `spore_performance-1.0.0.jar` 不具备继续迭代式替换正式整合包的发布条件。**

这不是指它全部无效：最低限度的三件套隔离服可以加载，新生成的适应型 Hohlfresser 也能跨越多个同步周期运行。但是，尚未精确复现“旧存档加载、子分体 UUID 暂时不可解析且恰逢第 20 Tick 适应同步”的原始崩溃条件，不能把该 NPE 记为完全验证。同时，当前版本把未完成验收的 AI 重构、灾厄导航和多项激进节流同时设为正式启用；还存在已证实的 Mixin 覆盖冲突、配置承诺未落地和不可复现构建问题。应先完成下文 P0 批次，再进行按功能分组的隔离验证，最后才允许一次正式替换。

正式安装的候选文件：

`E:\斗蛐蛐\.minecraft\versions\国潮红师2\mods\spore_performance-1.0.0.jar`

SHA-256：`01DD245E6F771574BD346E2AE94ED927006BAB1055809D4051F49D8F3ADD1E5B`

## 证据范围

- 安装版 Spore `2.2.0j`、AI Fix `1.0.0`、sporesrp `1.7.2` 与 Harium 的实际 JAR/源码。
- SporePerformance 共 **72** 个 Mixin 源文件；配置声明 **52** 个服务端、**21** 个客户端 Mixin；包含 **11** 个 `@Overwrite` 声明和 **50** 个 `@Redirect` 声明。
- 正式配置 `spore_performance-common.toml`：AI 重构、共享感知、群体协作、共享路径、异步路径、灾厄导航以及多项 `[aggressive]` 均为 `true`。
- 用户提供的三类崩溃记录，以及隔离 Forge 47.4.22 专用服的实际启动与实体复现测试。

本报告不把 TerritoryControlCompat 的独立 Mixin 崩溃归因到 SporePerformance；该问题需由界域沙盘修复。

## 已复核的崩溃

| 记录 | 实际原因 | 归属 | 当前状态 |
| --- | --- | --- | --- |
| `territorycontrolcompat...SporeFoliageSpreadMixin contains non-private static method` | 界域沙盘 Compat 的 Mixin 方法可见性不合法。 | TerritoryControlCompat | 与本模组无直接因果；仍会阻止全整合包启动。 |
| `UndergroundMovementControl...rejectCircularUndergroundYaw` 注入 0/1 | 本模组和 AI Fix 都改写 `moveUnderground`；AI Fix 先 `@Overwrite`，我方 Redirect 找不到原调用点。 | SporePerformance × AI Fix | 当前插件以 `AI_FIX_OWNED_CONTROLLERS` 跳过我方该 Mixin，隔离服已验证能加载；这是回避冲突，不是完成 Hohlfresser 导航重构。 |
| `Hohlfresser.m_8119_` 第 324 行空分体 NPE | Spore 先创建含 null 的 `parts` 数组，再在 20 Tick 适应同步中解引用，最后才创建分体。 | Spore 本体 | 候选 JAR 将原生 `createSegments()` 前移至同步前，并对移除/尸体/part list 加空值保护。隔离服的新生成 `{adaptation:1b}` 实体持续运行无异常；尚未精确覆盖旧存档子分体延迟解析的原触发窗口。 |

## P0：必须在任何再次部署前处理

### P0-1：构建不可复现，当前源码不能重新产出候选 JAR

`build.gradle` 第 40 行硬编码依赖 `mods\sporesrp-1.7.2.jar`，而实际安装文件为 `[真菌感染：逃逸]sporesrp-1.7.2.jar`。使用现有 Gradle Wrapper 执行 `clean check` 在项目配置阶段失败，尚未编译或测试任何代码。

影响：无法证明正式 JAR 对应当前源码，也无法在改动后完成最基本的编译、测试、重混淆和包内容校验。

修复门槛：依赖定位改为确定的、启动前校验过的路径（或受控 ASCII staging 输入）；构建必须先通过 `clean check verifyMixinPackageSafety jar`，并把产物 hash 写入发布记录。

### P0-2：Stahl 的五个核心方法被 AI Fix 覆盖，当前“重构开启”状态是错误报告

AI Fix 的 `StahlMeleeAttackGoalMixin` 与本模组 `AiFixStahlMeleeGoalMixin` 都 `@Overwrite` 下列五个方法：`m_8045_`、`resetAttackCooldown`、`checkAndPerformAttack`、`startDelayedAttack`、`m_8037_`。AI Fix 默认优先级高于我方 `900`，因此安装 AI Fix 时由 AI Fix 的实现赢得覆盖；我方的性能/行为版本并不能作为实际逻辑保证。

影响：

- 配置和 `/status` 将“AI 重构”描述为生效，但 Stahl 攻击主状态机并未由该重构控制。
- 两套实现同时维护落地伤害、数值、粒子与上升方块，后续稍有优先级或签名变化即可产生双效果、缺效果或启动失败。

修复门槛：二选一，不能继续叠加：

1. 在 AI Fix 存在时完全委托其 Stahl Mixin，并移除我方五个覆盖和相关“已接管”状态；或
2. 将 AI Fix 的 Stahl 修复完整、逐项迁移到单一我方实现，并由插件在类转换期排除 AI Fix 的 Stahl Mixin（需在最小栈和完整栈做功能回归）。

### P0-3：灾厄“单一身体朝向写入者”并未实现，正是转圈回归的结构性原因

`CalamitySmoothLookControlMixin` 只在 `SmoothLookControl` 结束后还原身体 yaw；`CalamityMovementControlMixin` 与 `HindenburgLookControlMixin` 仅拦截两个特定 `setYRot` 调用。实际控制来源至少还包括：

- AI Fix 的 `UndergroundMovementControlMixin`（Hohlfresser）；为避免 P0-0 崩溃，我方 Mixin 当前被跳过；
- AI Fix `GrakensenkerMixin` 在 tick 尾部直接写 `YRot/YBodyRot/YHeadRot`；
- Stahl 专属移动/跳跃控制；
- 特殊导航和原生 `moveTo` 的目的地反复重置。

`CalamityNavigationRuntime.yawOwner()` 目前主要是字符串诊断；它没有统一仲裁这些写入源。因此“singleYawOwner=true”并不等于只有一个写入者。对大体积实体，路径终点/首节点仍在自身碰撞体内时，重复 `MOVE_TO` 仍会驱动原地小圈。

修复门槛：先建立每类灾厄的**意图输入 → 唯一移动执行器 → 身体 yaw 输出**表；仅在一个适配层写 body yaw。LookControl 只能写头部/俯仰。到达判定必须使用实体宽度、目标类别（动态实体/静态 SearchArea）和路径终点，而非仅限制转向速度。Hohlfresser 的 AI Fix 控制器须通过一个明确适配器接入，不可再次对它的已覆盖方法插 Redirect。

### P0-4：200 “活动单位”配置没有实现所承诺的全体后台休眠

`FungalWorkBudget` 的 `maxActiveFungalUnitsPerDimension=200` 仅被 `RemoteIdleAiController.isDormant()` 使用；后者只会让远距、空闲的 Basic/Evolved/Hyper 每 20 Tick 跳过 GoalSelector。它不暂停所有超额 Spore 的自定义 Tick、目标传播、导航请求、范围扫描或灾厄/器官体工作。`GastGeber/Mound/Tendril` 的工作令牌只保护已接入的个别传播方法。

影响：旧存档中大量已加载单位仍可以同时做高成本工作，配置文字“同时执行完整 AI”与真实执行不符。

修复门槛：将“生成上限”“后台工作令牌”“完整 AI 活动集”拆成三个独立、真实的状态。超额实体必须在统一 Tick 管线入口就只保留物理、受击、生命、同步和近玩家/战斗唤醒；其余感知、群组、路径、感染与随机行为由活动令牌控制。旧实体仍不删除。

### P0-5：Mixin 签名探测不能证明注入点兼容，状态会假阳性

`SporePerformanceMixinPlugin` 多数只检查目标类“是否包含方法名”。它通常不校验 `@Redirect` 的被调用 owner/descriptor/ordinal、Shadow 字段、Overwrite 的完整 descriptor，亦不报告同目标的其它 mod 覆盖优先级。Underground 崩溃已经证明“目标方法存在”不足以保证兼容。

修复门槛：为每个直接覆盖/Redirect 建立 descriptor 级 manifest；在 `preApply/postApply` 检查转换后目标，记录唯一状态。对 AI Fix、sporesrp、Sona 适配必须按功能单独 fail-closed。不能把 `require=0` 的静默失效显示为 ACTIVE。

## P1：高风险语义/性能问题

| ID | 发现 | 证据与影响 | 修复方向 |
| --- | --- | --- | --- |
| P1-1 | `FoliageSpreadMixin`、`InfectionTendrilMixin`、`GastGeberMixin` 使用 `@Overwrite`。 | 开关关闭时仍运行“复制的 legacy 实现”，而不是原目标方法；后续 Spore 小版本变化会产生静默语义漂移。 | 将安全缓存移到精确调用点，或仅保留经过反编译 bytecode 对照的单一实现；所有覆写放入实验组。 |
| P1-2 | 通用 `PathNavigationMixin`、三种专用导航 Mixin 与 Calamity 导航运行时叠加。 | 同一 `createPath/moveTo/isStuck/tick` 可能经过两至三个入口；缓存、回退和状态机的所有权不单一。 | 收敛为一个 Path 请求边界；每种导航类型只注册一个适配器。 |
| P1-3 | 异步路径在正式配置默认开启。 | 虽然 worker 只跑 `GridPathfinder`，主线程每请求仍同步扫描最高 64×64×7 方块建立快照；该粗走廊没有灾厄适配，且产物须再次本地寻路。 | 默认关闭到达到主线程采样收益后；按导航类型白名单；为快照本身加时间预算。 |
| P1-4 | `FungalEntityIndex` 以强引用保存全部已加载 `LivingEntity`。 | 有 Join/Leave/ChunkUnload 清理，但运行时首次建立时没有同步 bootstrap；首轮/事件遗漏可得到不完整候选集。 | 首次 level runtime 建立时仅遍历已加载实体一次；其后用生命周期增量维护；对漏项退回原世界查询。 |
| P1-5 | 客户端 Sona/Oculus 和渲染兼容是直接 GL/Redirect 修改。 | 最小专用服不覆盖客户端；深度纹理、Overlay 批处理、Embeddium 和 AcceleratedRendering 均未与当前完整客户端同矩阵验证。 | 将客户端改动独立为客户端实验包；固定 Oculus/Voxy/Embeddium/Sona 矩阵和 10 分钟 GL 验收。 |
| P1-6 | sporesrp 的 `scanForSurface` 被无条件 `@Overwrite`，即使调度开关关闭也经过本模组的同步复制实现。 | 配置注释称“仅激进模式”，实际仍替换原方法。 | 显式恢复原方法或将该 Mixin 放入独立实验配置；验证搜索顺序、加载区块语义和失败重试。 |

## P2：可维护性与正确性风险

- `ServerLevelEntityTickMetricsMixin` 在 `tickNonPassenger` HEAD push ThreadLocal、RETURN pop。实体 Tick 抛异常时不会 pop，后续同线程查询可能读到旧 Spore 上下文。AI Fix 也采用相同结构；应统一为可保证 finally 的包装式注入或在服务器 tick 边界清空。
- `NativeKey`/`PositionKey` 不包含目标 UUID，只按目标坐标桶区分。近距离目标切换时可短暂复用另一目标的 native Path。应在 key 中包含 UUID，或把缓存限定为同一目标对象。
- `FungalWorkBudget` 首次刷新前（默认 200 Tick）`mayWork()` 返回 true，已有实体可短时间全部执行感染工作。应在加载/首次实体加入时立即构建令牌。
- `FungalWorkBudget` 每轮对所有已追踪 Mob 排序，并对每个 Mob 遍历玩家计算距离；在高实体数时它本身是周期性尖峰。应使用空间索引或增量优先级队列。
- `SonaCanChunkTickCache` 使用 `ConcurrentHashMap<Level,...>`；虽有 unload 清理，客户端重载/异常路径仍应将缓存绑定到世界 session token，避免旧 Level 作为键滞留。
- 当前客户端/服务器的诊断日志默认部分开启（灾厄追踪），需确保采样/写盘不会在大规模战斗中反过来制造 MSPT 尖峰。

## P3：性能设计未证实或低收益项

- `FungalEntityIndex` 使用 boxed `HashMap<Long, Set<LivingEntity>>` 和 `LinkedHashSet`；候选集高频建立 `ArrayList`。在完成功能正确性前不应继续微优化；后续可换 primitive section key 与数组池。
- `routeStamp()` 对完整原生 Path 的每个 node 生成 `BlockPos`/Map；短 TTL 高频缓存可能把路径求解成本转为分配压力。需要 Spark allocation 证明收益。
- sporesrp 维度分桶在当前“只有一个维度”场景的收益有限，优先级低于实体/导航问题。
- 多数 `PerformanceMetrics.increment("动态字符串")` 虽在 diagnostics 关闭时较轻，但动态键仍应避免出现在频繁循环内。

## 当前配置风险快照

正式配置启用了下列尚未完成发布验收的行为：

- `[refactor.ai]` 全部开启；
- `[refactor.navigation]` 包含 `asyncLongPaths=true`；
- `[refactor.calamityNavigation]` 全部开启；
- `moundTendrilScheduler`、`foliageScheduler`、路径退避、均衡目标搜索、普通路径退避、静止物品物理 LOD、孤立投射物清理、远距空闲 AI、Howitzer 轨迹缓存、群体感知、sporesrp 任务调度等均为 true。

这些设置并非“只改善性能”的安全配置。它们应在修复批次完成前移至隔离测试 profile；正式 profile 只保留已经有等价性证据的、无覆盖冲突的缓存/索引项。

## 通过的最小验证与其边界

隔离目录：`SporePerformance-1.20.1\run\runtime-smoke`。

通过：Forge 47.4.22 + Spore + AI Fix + sporesrp + Harium + 当前候选 JAR 专用服启动；RCON 生成 `spore:hohlfresser` 并执行 `data merge ... {adaptation:1b}`；跨多个 20 Tick 分体同步周期，无 `NullPointerException`、`MixinTransformerError` 或实体 Tick 异常；随后正常保存停止。

边界：这个用例会让新实体在首 Tick 后自然建立分体，因此不能等同于原崩溃所需的“旧父实体先重建出空 `parts`，子实体因加载时序尚未解析，而恰好在第 20 Tick 执行适应同步”。该精确用例仍是发布门槛，未完成前 Hohlfresser 修复只能标记为“候选有效”，不能标记为“已证实”。

未通过/尚未覆盖：当前完整客户端（Connector、Oculus、Voxy、Sona、Embeddium、AcceleratedRendering）、全存档实体密度、八类灾厄追击/待机/卡路、sporesrp Proto/Full Hivemind/Builder、客户端 HUD 和渲染链。

## 统一修复与验证顺序

1. **重建发布链**：修复 sporesrp 构建输入定位；每次构建输出 JAR hash、`mods.toml`、`pack.mcmeta`、Mixin 清单和 refmap 校验。
2. **拆分风险域**：核心安全包只保留已证实不冲突的优化；AI 重构、灾厄导航、sporesrp 覆写、客户端 GL/渲染改动分别放独立实验 Mixin 配置/独立附属 JAR。不能用运行期 TOML 去规避已经发生的 Mixin 冲突。
3. **单一所有权重构**：先处理 Stahl 与 AI Fix 的五个 Overwrite，再处理每类灾厄的路径/移动/yaw 所有权；没有统一状态机前不再增加旋转抑制器。
4. **兑现工作上限**：将 200 活动单位从“远距 selector 节流”改为真实的阶段门控，并让工作令牌即时初始化、可诊断。
5. **descriptor 级兼容门控**：为全部 20 个 Overwrite 和 53 个 Redirect 建 manifest，任何不匹配、重叠或未知优先级均 fail-closed。
6. **完整矩阵**：每个风险域先在最小服务端验证，再加入 AI Fix、sporesrp、Harium，最后在完整客户端做启动、实体行为、Spark 和 GL 矩阵。一次测试失败后回到对应风险域，不直接把新 JAR 放入正式 mods。

## 发布门槛

以下必须同时满足：

1. `clean check verifyMixinPackageSafety jar` 成功，正式 JAR 由该构建直接产生并记录 hash；
2. P0 全部关闭；
3. 最小和完整矩阵均无 Mixin apply/injection error；
4. Hohlfresser 的旧存档子分体延迟解析＋第 20 Tick 精确复现用例，以及 Stahl、Hinderburg、Gazenbrecher、Grakensenker、Leviathan 等分组行为回归均通过；
5. 当前存档副本的 180 秒 Spark 和 `--only-ticks-over 35` 慢 Tick采样完成；
6. 客户端 GL 连续 10 分钟无 `GL_INVALID_OPERATION`，且视觉回归通过；
7. 只在上述证据齐全后，备份并一次性替换正式 JAR。

## 审计后修复记录（2026-08-24）

用户确认继续保留激进优化，并将“灾厄转圈”及 Hohlfresser 的旧存档精确复现移出本批次；以下其余结构问题已经在源码和隔离服中处理：

- **可复现构建**：`build.gradle` 不再硬编码不存在的 `sporesrp-1.7.2.jar` 文件名，而是只接受 mods 目录中唯一匹配稳定版本后缀的 JAR，再复制至 ASCII staging 目录。`clean check` 与 `jar/reobfJar` 已成功。
- **Stahl 单一状态机所有权**：安装 AI Fix 时，我方 `AiFixStahlLeapGuardMixin`、`AiFixStahlmorderControlMixin`、`AiFixStahlMeleeGoalMixin` 全部跳过；AI Fix 保留其完整的攻击、落地伤害、数值、粒子和方块特效实现。没有 AI Fix 时，移植实现才会接管。
- **真实活动 AI 上限**：对 Basic/Evolved/Hyper，超出 `maxActiveFungalUnitsPerDimension` 的远距闲置单位会在 `Mob.serverAiStep` 入口被暂停，因此不执行感知、Goal、寻路、导航或移动控制；物理、受击、生命与同步仍逐 Tick。近玩家、受击、已有目标的单位只能在固定槽位内抢占空闲槽位。隔离服将生成上限临时设为不限、活动上限设为 10，生成 40 个感染人后状态为 `loaded=41, active=10`。
- **Mixin 指令契约**：新增启动期 ASM 核对；`@Overwrite` 的完整 descriptor、以及必须命中的 `@Redirect` 调用/字段指令必须在目标字节码中存在，否则整个补丁 fail-closed，不再等待 Mixin 报注入异常。`require=0` 的 AI Fix 后置可选钩子保留为可选，不会因原始类尚未被 AI Fix 增强而误判。
- **隔离加载**：Forge 47.4.22 + Spore + AI Fix + sporesrp + Harium 用新产物启动通过；Hohlfresser、Howitzer、Stahl、Grakensenker、Hindenburg、Gazenbrecher、Sieger 的类转换和实体 Tick 均未出现 `MixinTransformerError`、`InvalidInjectionException`、`NullPointerException` 或实体 Tick 异常。

本记录不等同于完整客户端、正式存档或 Spark 性能验收；正式 JAR 在该轮验证前仍未替换。
