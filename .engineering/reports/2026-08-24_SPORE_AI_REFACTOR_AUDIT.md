# Spore 2.2.0j 实体 AI 底层审计与重构覆盖报告

## 权威与证据

- 行为权威：安装版 `[真菌感染：孢子]spore_1.20.1_2.2.0j.jar`。
- 可读参考：`Spore_2.0_1.20.1-master`；只用于定位，所有注入点以安装版 `javap`/ASM 签名复核。
- 覆盖栈：Spore、`exhuashan_sporeai_fix-1.0.0`、`sporesrp-1.7.2`、SporePerformance。
- 动态基线：Spark `xQ4yNPbQVj` 的慢 Tick 采样中，实体 Tick 约35 ms，Spore归属约16.2 ms；`Mob.serverAiStep`约8.7 ms、最近目标约3.8 ms、路径创建约3.2 ms。各数字存在父子调用关系，禁止直接重复相加。

## 全量静态清点

源码扫描覆盖整个 `Sentities` 目录，而非只扫描类名含 AI 的文件。检测到133个实体/控制类覆盖 Tick 路径，共204处；70个文件包含124处实体范围查询；28个文件包含68处路径请求；46个文件包含83处LOS检查；27个文件包含46处方块立方体扫描。

| 家族 | 相关文件 | Tick覆盖 | 实体查询 | 路径请求 | LOS | 方块扫描 |
|---|---:|---:|---:|---:|---:|---:|
| 公共AI/Goal | 32 | 23 | 10 | 33 | 13 | 2 |
| 基类 | 15 | 10 | 9 | 2 | 5 | 7 |
| 基础感染体 | 6 | 10 | 0 | 2 | 0 | 1 |
| 进化感染体 | 26 | 43 | 24 | 11 | 26 | 3 |
| Hyper | 7 | 14 | 7 | 4 | 7 | 1 |
| Calamity | 9 | 24 | 18 | 1 | 15 | 9 |
| Organoid | 11 | 22 | 32 | 0 | 10 | 8 |
| 移动/导航控制 | 9 | 9 | 0 | 5 | 0 | 1 |
| Utility | 13 | 26 | 13 | 9 | 6 | 13 |
| Projectile | 21 | 18 | 6 | 0 | 0 | 0 |

## P0/P1发现

| 等级 | 调用链与触发 | 证据 | 重构处理 |
|---|---|---|---|
| P0 | `CalamityPathNavigation/HybridPathNavigation.moveTo(Entity)`路径为空仍返回成功，后续`tick/isStuck/recomputePath`形成失败重算环 | 安装版签名`m_5624_ → m_6570_`、`m_26577_ → m_26569_`；Spark路径创建约3.2 ms | 新导航返回真实成功状态；同键请求合并；原生短路径副本共享；长路径只读快照异步粗走廊；特殊导航失败关闭回退 |
| P1 | `NearestAttackableTargetGoal.findTarget`由每只实体分别扫描相似AABB并执行Predicate | Spark最近目标约3.8 ms；感染体基类同时注册两项同优先级最近目标Goal | 每维度三维区段索引＋同空间单元一Tick候选帧；每只实体重新执行原`TargetingConditions`并稳定选择最近ID |
| P1 | `HurtTargetGoal.alertOthers`和`LocalTargettingGoal.Targeting`对每次受击/目标变化重新扫描同类，群体密度下形成重复扇出 | 安装版`alertOthers`、`Targeting`；源码32格/FollowDistance扫描 | 事件驱动威胁传播；共享群组成员表；保留同类、驯服主人、联盟、Linked、忽略类型和目标有效性 |
| P1 | `FollowOthersGoal`每个感染体周期搜索32格伙伴，并在路径完成后反复`moveTo` | 安装版`findNearestPartner`、`m_8037_`；群体规模放大 | 从共享成员表稳定选择最近伙伴；移动请求进入共享导航；不改变3格停止、64格失效和LOOK行为 |
| P1 | `CustomMeleeAttackGoal/AOEMeleeAttackGoal`在`canUse`先创建路径，运行期目标移动时再次`moveTo(Entity)`；AOE再独立扫描目标周围实体 | 安装版`m_8036_`、`m_8037_`、`checkAndPerformAttack` | 路径键共享和请求合并；AOE只在实际挥击帧从共享索引取候选，再执行原Predicate和伤害逻辑 |
| P1 | 自定义实体Tick中的范围查询散落在Gorgon、Naiad、Howitzer、Hollenhund、Vanguard、器官体和灾厄类 | 124处实体查询；Organoid家族11文件含32处 | 服务端实体Tick上下文统一接管LivingEntity查询，使用共享感知帧并精确重放原AABB和Predicate；非LivingEntity查询回退原逻辑 |

## P2/P3发现

- P2：83处LOS调用中很多来自目标检测和运行Goal。原版`Sensing`已有同观察者同Tick缓存，因此没有叠加跨Tick不透明缓存；新增计数用于识别真正重复调用，攻击帧仍走精确LOS。
- P2：Gorgon、Naiad、Howitzer、Proto、Hollenhund、Vanguard含多组查询/路径/LOS组合，是动态指标优先观察对象；它们的通用查询已由Tick上下文接管，技能状态机未被替换。
- P2：多段实体位置必须逐Tick同步；只缓存父实体解析和状态传播，不降低命中箱更新频率。
- P2：46处方块扫描继续由已有菌丘、卷须、侵蚀和sporesrp游标调度处理，不塞入AI工作线程。
- P3：Goal中的配置读取、重复AABB/BlockPos分配和调试反射属于低优先级清理；不得用这些微优化代替结构重构。

## 导航分类与边界

| 导航 | 共享原生路径 | 异步粗走廊 | 说明 |
|---|---|---|---|
| Ground/普通PathNavigation | 是 | 是 | 地面快照检查脚、头和地板碰撞；工作线程只读数组 |
| Hybrid/Calamity | 是 | 否 | 已修复空路径假成功；介质切换和破坏能力保持原NodeEvaluator |
| Fly/Swim/Water | 是 | 否 | 三维介质语义复杂，第一版不使用二维粗走廊 |
| Climb/Underground | 是 | 否 | 墙面和钻地语义交给原导航，仍享受请求合并及短期路径共享 |

异步结果只提供中间走廊点，最终局部Path仍由实体自己的导航器验证。任何快照失败、队列满、签名漂移或特殊导航不兼容均回退原路径，不强制加载区块。

## AI Fix与sporesrp覆盖

- AI Fix覆盖Howitzer/Hinderburg/Stahl/Verfall等专属攻击、飞行、地下移动、永久实体生命周期。Stahl的Leap、空中转向、落地恢复、延迟近战、数值加强和完整落地视觉/伤害均已按要求纳入；AI Fix存在时对应Mixin整体跳过，避免重复结算。
- sporesrp Proto与完整心智的技能、挖掘和Builder保持原适配；Proto作为Spore实体自动进入共享感知索引，附属类缺失时不产生硬引用。
- 新增Spore专属Mixin均由ASM检查目标方法名；不兼容时单项`INCOMPATIBLE`，其余模块继续启动。
- AI Fix不再仅作为黑盒绕开：灾厄指挥、搜索命令、Hyper命令优先级、Hinderburg目标替换、Howitzer/Hinderburg互斥Goal、Stahl完整Leap/空中控制/可靠近战、Grakensenker漏斗扫描和水生节点修复已纳入共享运行时边界。完整分类见`2026-08-24_SPORE_AI_FIX_INTEGRATION.md`。
- 不朽君王龙、永久实体回填、死亡/移除保护和最终伤害硬上限明确不进入SporePerformance。

## 运行诊断

- `/sporeperformance status`：显示开关、索引实体数/区段数、感知帧、路径队列、在途任务和缓存。
- `/sporeperformance metrics`：目标帧构建/复用、候选数、威胁扇出、伙伴查询、路径缓存/合并/结果、LOS、Goal和各实体完整Tick耗时。
- `shadowComparison=true`时同时执行一次绕过重构路由的原世界目标查询，只记录目标一致/不一致，不改变最终结果。

## 尚需真实存档验收

构建、单元和启动烟测只能证明签名、线程边界和启动安全；3.8 ms目标、3.2 ms路径、16.2 ms Spore实体成本以及中位/P95 MSPT目标必须在同一存档重启后重新采样。报告不把静态预期写成已达成的动态收益。

## 构建、兼容与部署验收

- Java 17 `clean build`通过，包含配置注释、Mixin包安全和`GridPathfinder`测试。
- 安装版运行签名矩阵全部通过：Calamity/Hybrid导航、Hurt/Local/Follow/AOE、Mound、Spawner、AI Fix及sporesrp目标均存在。
- 隔离服务端实体探针通过五组：仅Spore、Spore＋AI Fix、Spore＋sporesrp、完整三件套、完整三件套＋Harium。
- Harium首次对照发现双方同时重定向`ProjectileUtil`宽相查询，会在BileProjectile首次Tick触发零命中InjectionError。现已按软兼容规则在`harium`存在时跳过本附属的重复补丁，由Harium负责通用投射物碰撞；复测通过。
- 构建JAR含有效`pack.mcmeta`、`mods.toml`和Mixin配置。整合完整Stahl AI、数值、落地表现及AI Fix条件委托后的正式JAR SHA-256为`FED5B11BCB3AAE9DE4D3DB0EA1CC3EBD44CB5FAE1887CC75672C746B4B6B6F89`。
- 旧JAR已备份为`spore_performance-1.0.0.jar.backup-20260824-0235-ai-refactor`；正式配置保留原激进选项，并新增默认开启的`refactor.ai`和`refactor.navigation`。
