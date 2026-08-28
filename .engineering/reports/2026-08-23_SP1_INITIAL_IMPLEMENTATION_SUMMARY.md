# Spore Performance 1.20.1：初始实现总结

## 工作单元

- ID：`2026-08-23_SP1_INITIAL_IMPLEMENTATION`
- 项目：Spore Performance（新建、非 Git 子项目）
- BASE：工作开始时 `SporePerformance-1.20.1` 尚不存在；因此不存在需要保全的项目源码或提交。父工作区是脏 Git 工作树，本工作单元没有修改其中任何既有文件。
- 实现凭据：`.engineering/diffs/2026-08-23_SP1_INITIAL_IMPLEMENTATION.diff`

## 目标与范围

实现 Forge 47.4.3 / Java 17 / Minecraft 1.20.1 的 Spore 性能附属基础版本。Spore 2.2.0j 为硬依赖；AI Fix 和 sporesrp 为软依赖。安全优化默认开启，可能改变完成时机的调度与 AI 降频受 `[aggressive].enabled=false` 总开关保护。

未修改正式存档、现有整合包 `mods` 目录、Spore、AI Fix、sporesrp 或 TerritoryControlCompat。

## 当前实现

### 安全路径（默认开启）

- Overgrown Spawner：客户端取消 `feed` 世界扫描；服务端以维度/区块位置索引查找已加载的 `LivingStructureBlocks`，保留原实体供养数值和频率。
- 菌丘感染映射：覆盖 `FoliageSpread.convertBlocks`，把字符串映射的注册表解析移到配置加载/重载；关闭该项时回退原逐条解析逻辑。
- 灾厄导航：同一 `CalamityPathNavigation` 同 Tick 最多一次路径重算。
- 感染体 Follow Goal：跳过非 `EvolvingInfected` 的必空 Calamity 伙伴搜索。
- AI Fix：同 Tick 同目标位置复用 Howitzer 弹道 LOS 结果；客户端 Hinderburg 可视效果使用加入/离开事件维护的实体索引。
- sporesrp：Proto、标记菌丘、完整心智和 Gastgaber Builder 的 UUID 查询使用共享维度提示，已知处于其他维度时直接返回空；不持有 Level 或实体的强引用。

### 激进路径（默认关闭）

- 菌丘卷须：4,096 方块/任务/Tick、16,384 全局/Tick、64 任务上限、未加载区块跳过、队列满后 100 Tick 重试。
- 菌丘侵蚀：单一球形游标把结构放置与感染处理合并，2,048 方块/任务/Tick、8,192 全局/Tick、128 任务上限。
- 灾厄寻路：最小 10 Tick，失败后 20/40/80 Tick 退避；目标移动超过两格或生物刚受伤时解除退避。
- Howitzer：缓存期扩展至最多 5 Tick，目标移动超过 1.5 格失效。
- 远距空闲 AI：仅 `Infected` 且排除 Calamity/Organoid；超过 96 格、无目标、100 Tick 未受伤、未骑乘时，仅跳过 GoalSelector/TargetSelector 的部分 Tick，物理、导航、效果、生命和同步仍逐 Tick 运行。

## 模块边界

| 模块 | 职责 | 状态与失败边界 |
| --- | --- | --- |
| `config` | Common/Client 配置规格 | Forge 重载；Mixin/可选模组检测需重启 |
| `world` | 方块实体索引、感染映射、远距 AI | 只保存位置或弱键；停服清理 |
| `scheduler` | 有界菌丘队列 | 关闭激进模式时取消附属任务，由 Spore 下次自然周期恢复 |
| `mixin` | Spore/Forge 注入与可选 Mixin 选择 | 每个可选注入 `require=0` 或类存在性检查，失败不影响基础补丁 |
| `compat` | 运行期签名探测、缓存、维度提示 | 反射失败关闭相应兼容功能并记录一次日志 |
| `client` | Hinderburg 客户端索引 | 仅客户端加载，世界卸载时清空 |

## 构建、制品和验证

- 构建命令：`gradlew.bat -p SporePerformance-1.20.1 clean build --no-daemon`
- 结果：`BUILD SUCCESSFUL`；无单元测试源码（`test NO-SOURCE`）。
- 制品：`build/libs/spore_performance-1.0.0-SNAPSHOT.jar`，67,735 bytes。
- SHA-256：`3BC99D0C05F02473A419D5F028949D44400208C3C8F6AB414A0979C3DEFE3956`。
- 隔离服务端启动：用户已授权并在工程 `run/eula.txt` 接受 EULA。`runServer --no-daemon` 完成 Mixin 配置准备并进入模组构造；第一次发现并修复了接口 Mixin 类型错误。随后 Spore 自身在 Forge 开发映射环境报 `ResourceLocation.m_135815_()` 缺失：安装 JAR 通过 `files(...)` 未被 ForgeGradle 反混淆，属于开发运行时的 Spore 映射冲突，发生在 Spore 构造、Spore Performance 创建前。
- 已用安装 JAR 的 `javap` 核验关键运行签名：`Mound.SpreadKin`、`Mound.m_8119_`、`FoliageSpread.convertBlocks`、`FollowOthersGoal.m_8036_`、`CalamityPathNavigation.m_26577_/m_26569_`、Overgrown Spawner `feed`、AI Fix Hinderburg 渲染枚举、sporesrp `m_8791_` 查询。

编译仅有 Forge 1.20.1 API 弃用警告；无编译错误。外置 JAR 以 `files(...)` 提供，ForgeGradle 提示其不会自动反混淆；因此所有针对当前安装 JAR 的 Spore/sporesrp/AI Fix Mixin 明确使用运行时混淆签名或 `remap=false`。

## 兼容矩阵与技术债

| ID | 作用面 | 本项目 Hook | 对端 | 失效表现 | 当前保护/回退 | 验证方式 | 下一动作 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SP-001 | Spore 运行签名 | 所有 Spore Mixin | Spore 非 2.2.0j | 注入点变化导致启动或行为问题 | `[2.2.0j,2.3)` 依赖范围、JAR 签名核验、激进项默认关闭 | 完整三件套隔离启动 | 逐版重新跑启动矩阵 | MITIGATED |
| SP-002 | AI Fix | Howitzer/Hinderburg Mixin | `exhuashan_sporeai_fix` | 类或覆盖方法变更 | 类资源检查、运行期反射探测、可选 Mixin | Spore+AI Fix 客户端/服务端 | 完整客户端启动与弹道回归 | OPEN |
| SP-003 | sporesrp | UUID 维度守卫 | sporesrp 1.7.2 | Handler 内部签名变化或跨维缓存陈旧 | 探测失败关闭；实体离开/停服清理 | Spore+sporesrp 专服 | Proto/完整心智/Builder 回归 | OPEN |
| SP-004 | 激进扫描 | FungalWorkScheduler | 菌丘事件 | 任务完成延迟或任务取消边界 | 默认关闭、限额、死亡/失维自然取消 | 隔离存档菌丘压力场景 | Spark S0-S4 与行为回归 | OPEN |
| SP-005 | Mixin 应用 | 接口 overwrite、Mob 注入 | Forge/Mixin 0.8.5 | 类加载后才出现的注入错误 | 已修复配置准备错误；`require=0` 用于可选钩子 | 接受 EULA 后的完整隔离启动 | 载入 Mound/Overgrown/Calamity 实体并查日志 | OPEN |

## 后续工作

1. 用真实运行栈的隔离副本（而非 ForgeGradle 映射开发运行）完成仅 Spore、Spore+AI Fix、Spore+sporesrp、三件套的客户端和专服启动矩阵。
2. 建立 S0-S4 Spark 存档，采集优化前/安全/激进三次 180 秒数据。
3. 完成尚未落地的第二批激进项：群体感知共享快照、sporesrp 完整心智惰性球形挖掘、Proto/完整心智/Builder 错峰和 Howitzer 新轨迹预算。
4. 进行最高强度全盘代码审查，重点审查 SP-001 至 SP-005。
