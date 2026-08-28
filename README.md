# Spore Performance 1.20.1

## 服务端实体与掉落物优化

- 内置 Spore 掉落物区块批量合并默认关闭，避免与 Harium/Lithium 重复；常见生物质废料默认 60 秒、普通 Spore 掉落默认 120 秒消失。
- 玩家主动丢弃、命名、附魔、损伤、不可堆叠和自定义 NBT 物品保持原版保护时间。
- 旧的2–5 Tick目标错峰和20/40/80 Tick路径退避只在AI重构总开关关闭时作为兼容回退；重构默认使用共享感知和请求合并。
- 已保存的超额实体不会删除；GastGeber、Mound、卷须和全部真菌单位分别使用可配置工作名额。
- Hohlfresser 分体父实体查找与 Spore 投射物宽相碰撞候选按同 Tick复用。
- `/sporeperformance status` 可查看物品协调器、生成上限与实际工作名额；详细计数需开启 `diagnostics.metrics`。

## Spore AI 底层重构

- `[refactor.ai]` 默认启用维度级三维实体索引、共享感知帧、事件驱动威胁传播和稳定伙伴分配；原始目标Predicate、攻击、伤害与技能状态机不变。
- `[refactor.navigation]` 默认启用路径请求合并和短期原生路径共享。普通地面单位的长路径可使用不可变方块快照在后台生成粗走廊，最终局部路径仍由实体导航器在服务端线程验证。
- Hybrid、Calamity、飞行、水下、攀爬和地下导航均享受请求合并；不适合二维快照的特殊导航自动保留原NodeEvaluator。
- Spore实体自定义Tick内的LivingEntity范围查询统一进入共享索引；非生物查询、客户端和非Spore调用不接管。
- `[diagnostics.aiRefactor] metrics=true` 可按实体类型统计完整Tick、目标、LOS、群体广播和路径队列；`shadowComparison=true`只比较新旧目标结果，不改变行为。
- 完整静态证据、导航边界和未完成动态验收见 `.engineering/reports/2026-08-24_SPORE_AI_REFACTOR_AUDIT.md`。

Spore `2.2.0j` 的 Forge 1.20.1 性能附属，modId 为 `spore_performance`。它硬依赖 Spore；`exhuashan_sporeai_fix` 和 `sporesrp` 是软依赖，绝不打包进本 JAR。

目标环境为 Java 17、Forge `47.4.3`、Official Mappings。已按实际整合包的 Spore `2.2.0j`、AI Fix `1.0.0`、sporesrp `1.7.2` 反编译签名验证。

## 安装

把 `build/libs/spore_performance-1.0.6.jar` 放进整合包 `mods`，不要替换 Spore、AI Fix 或 sporesrp 原 JAR。第一次启动会生成：

- `config/spore_performance-common.toml`
- `config/spore_performance-client.toml`

管理员命令：

```text
/sporeperformance status
/sporeperformance metrics
/sporeperformance metrics reset
```

客户端只读命令：

```text
/sporeperformanceclient status
/sporeperformanceclient metrics
/sporeperformanceclient metrics reset
```

`status` 会显示每个可选集成和 Mixin 补丁的 `ACTIVE`、`SKIPPED` 或 `INCOMPATIBLE` 状态。检测失败时该独立项会 fail-closed，不影响其他补丁或服务器启动。

## 真菌单位上限

`[limits]` 是独立于激进优化的常规保护：按**每个维度已加载实体**计数，默认最多 `200` 个 `spore` 的生物单位、`16` 个 `spore:mound`（菌囊/菌丘）和 `32` 个 `spore:tendril`（感染卷须）。三项可分别调整，填 `0` 即关闭该项。

达到上限时只取消新的实体加入；已有存档实体始终允许读档，也不会被删除。已加载的旧实体会计入容量，因此清理或自然消失到上限以下前，不会继续生成新单位。`arena_tendril` 是 Boss 机制实体，不计入感染卷须上限；所有 Spore `Mob`（包括菌囊与感染卷须）都计入 200 总量。

`[limits.calamity]` 独立控制灾厄：`maxTotal` 是单个维度内所有灾厄总数，`maxPerType` 是同一种灾厄上限；两项默认均为 `-1`（不限制）。这两个上限拦截自然生成、sporesrp 支援/技能、结构召唤和命令/刷怪蛋等新的实体加入；旧存档实体仍可加载并计入后续容量。

## 默认安全优化

`[safe]` 的所有开关默认 `true`，设计为不改变 Spore 的正常行为时序：

- 感染刷怪笼只在服务端执行供养，并用“维度 → 区块 → LivingStructureBlocks 位置”索引替代方块体积扫描。
- 菌丘感染映射在配置加载/重载时编译，扫描热路径不再 `split`、构造 `ResourceLocation` 或查询注册表。
- 感染卷须每 10 Tick 的局部传播复用该映射；只有通过原有 2% 转化概率的实心方块才读取六个邻格，避免其余 98% 位置的无效邻格读取。触发频率、转化概率、菌丝生成和容器/刷怪笼/遗骸交互保持不变；`tendrilSpreadFastPath=false` 可恢复原实现。
- 灾厄导航同一实体同一 Tick 至多触发一次 `recomputePath()`。
- 非 `EvolvingInfected` 不再执行必定无结果的灾厄伙伴搜索。
- AI Fix 存在时，Howitzer 同 Tick、同目标的 LOS 结果复用；客户端 Hinderburg 用实体加入/移除索引替换世界全量遍历。
- sporesrp 存在时，Proto、完整心智、标记菌丘和 Builder 使用维度桶与 UUID→维度提示，避免“维度数 × 记录数”的空查找。
- sporesrp HUD 由客户端附属接管：默认在普通游戏画面隐藏，打开背包、箱子或其他 Screen 时只在屏幕最前层绘制一次，避免被背景模糊并消除原实现的重复渲染。`renderHudInGameplay=true` 可恢复普通画面的单次绘制。

## 客户端真菌装饰距离剔除

`spore_performance-client.toml` 的 `[localRendering] fungalDecorationDistanceCull` 默认关闭。安装 Embeddium 后可以独立开启：目标方块只在观察点默认 `32` 格内加入区块网格，方块状态、碰撞、光照、选取和服务端逻辑均不改变。

当前目标为 `biomass_bulb`、`blomfung`、`bloomfung2`、`exploding_lump`、`fang_lump`、`fungal_clamp`、`fungal_stem_sapling`、`fungal_stem`、`fungal_stem_top`、`growth_mycelium`、`growths_small`、`remains` 和 `bile_lump`。玩家每移动默认 2 格更新边界，每 Tick 最多请求重建 8 个确实含这些方块的区段，避免一次性重建整个视距。

此功能不能与“Spore诊断-隐藏高密度真菌地表”全隐藏资源包同时使用；资源包会让 32 格内也没有原模型。改变开关或距离支持热重载，边界会按配置预算逐段恢复。

## Spore 实体模型与效果层

`[safe.sporeRendering]` 默认启用三项视觉等价优化：普通状态不再创建未使用的幻觉代理实体；感染来源到 `EntityType` 使用 256 项 LRU；父模型与附加膜层的 `setupAnim` 只有在同帧、同实体、同模型、所有动画参数逐位一致时才消除重复调用。

`[aggressive.sporeRendering.layers]` 与 `[aggressive.sporeRendering.animation]` 内的项目全部独立、默认关闭。眼睛、透明膜和发光层可以分别按相机平方距离剔除；Calamity、Hyper、Organoid 与 Proto 使用大型单位距离。动画 LOD 只降低远处 `setupAnim` 频率，实体位置、朝向、插值、物理和模型提交仍逐帧执行；受伤、死亡、挥击、姿态、骑乘、游泳和滑翔会立即刷新。每个缓存姿势都绑定“实体 ID＋模型实例”，缓存满后回退逐帧计算。

眼睛与 Howitzer 发光贴图的 Alpha 部件掩码也是独立开关。资源重载会重读已观察贴图，渲染时保留父子变换，只提交 UV 覆盖可见像素的部件；全透明、全不透明、反射签名漂移或模型结构不兼容时自动回退整模。

AcceleratedRendering `1.0.14` 为客户端软依赖。`[compat.acceleratedRendering]` 两个强制透明加速项默认关闭；启用后只在取得对应 `VertexConsumer` 的短临界区压入状态，并在 `finally` 中立即恢复。类或方法签名不符时状态显示为 `INCOMPATIBLE`，不会阻止客户端启动。

本地 Voxy Forge 修复将 opaque→translucent 的同格式深度/模板复制改为经过尺寸、格式和 framebuffer 完整性验证的 `glCopyImageSubData`，不再尝试会触发 `GL_INVALID_OPERATION` 的 framebuffer depth/stencil blit。Oculus→Voxy 的跨格式深度仍使用全屏着色器复制；Voxy→Oculus 仍使用深度变换着色器。deep diagnostics 下三段均有 GPU Debug Group，正常日志只保留一次格式/尺寸样本和异常变化。

## 激进优化

`[aggressive]` 没有总开关：每个布尔项都能独立启用，默认全部为 `false`。数值预算只有在对应功能项开启时才生效。这些项会改变后台完成时机或空闲响应；实体上限仅由上面的 `[limits]` 管理，不会自动删除实体，也不会改 Spore 的公开 API。

- 菌丘卷须：每任务 `4096`、全局 `16384` 方块/Tick，最大 `64` 任务；满队列请求保留并在 100 Tick 后重试。
- 菌丘感染/结构：每任务 `2048`、全局 `8192` 虚拟位置/Tick，最大 `128` 任务；结构放置和感染共用同一球形游标。`foliageFastCursor` 在读取世界前排除球体外位置并取消逐位置对象分配，`foliageDirectLoadedChunkRead` 只复用当前已经加载的区块，绝不强制加载新区块。
- 真菌后台时间预算：菌丝侵蚀和卷须搜索可分别启用 `1500`/`500` 微秒的单 Tick 时间上限；每 64 个位置检查一次，达到上限后任务回到公平队列继续执行，不丢弃游标。
- 群体感知：相同目标的邻居查询共享 5 Tick 空间 UUID 快照；Follow 伙伴使用 20 Tick 单元快照，并把后续原生 20 Tick 搜索按 UUID 错峰。首次搜索仍即时。
- 伙伴跟随路径：`followPathReuse` 复用仍有效且目标移动不足 2 格的路径，默认周期刷新为 40 Tick并按 UUID 错峰；`followPathFailureBackoff` 可独立启用 20/40/80 Tick失败退避。伙伴变更和明显移动会立即解除等待。
- 灾厄寻路：最小重算间隔 `10` Tick，连续失败为 `20/40/80` Tick 退避；目标移动超过 2 格或受击会立即解除。
- AI Fix Howitzer：轨迹缓存 `5` Tick，单发射体每 Tick 最多 `8` 个替换目标新轨迹；已有目标和开火不受此预算限制。
- sporesrp：完整心智半径 50 采矿队列改为常量内存游标；挖掘、地表搜索、外壳生成共享 `8192` 方块/Tick；地表与外壳只访问已加载区块，满队列请求保留。Proto/完整心智/Builder 可设 UUID 错峰因子；因子 `1` 保持原生节奏。
- 远距空闲 AI：仅 Basic、Evolved、Hyper 感染体在 96 格外、无目标、100 Tick 未受伤且未骑乘时每 10 Tick 运行 Goal/Target selector。Calamity、Proto、Organoid、投射物、方块实体、乘客和区块加载实体永不降频；物理、导航、效果、生命和同步仍逐 Tick 运行。

热重载会更新预算、距离、间隔和开关；关闭任务型优化会取消该附属任务，之后由 Spore 的下一自然周期接管。类签名、可选模组存在性和 Mixin 应用结果需要重启。

## 诊断与验证

`[diagnostics] metrics = true` 时，`/sporeperformance metrics` 输出轻量计数器；默认关闭。`tools/verify-runtime-signatures.ps1` 检查实际安装 JAR 的关键方法签名。`tools/run-runtime-smoke.ps1` 使用 `run/runtime-smoke` 隔离 Forge 服务器，不写入正式存档或正式 mods；`-ModuleSet spore|sporefix|sporesrp|full` 用于软依赖矩阵，`-Aggressive` 验证全部激进 Mixin 可加载。

完整动态压测场景、Spark 采样口径和验收表见 [最终交付报告](.engineering/reports/2026-08-24_SPOREPERFORMANCE_FULL_RELEASE_AUDIT.md)。不要把安全启动测试当作实战数值基准；在生产启用激进组前，请用隔离存档完成该表中的 S0–S4 场景。
