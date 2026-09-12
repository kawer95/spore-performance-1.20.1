# SporePerformance 1.0.12：Howitzer 服务端性能修复

## 目标与证据

Spark `yxOTk0uwOl` 将 Howitzer 远程 Goal、`CalamityPathNavigation.moveTo/createPath` 和 `ExpPathFinder.findPath` 指向同一条每 Tick 重复寻路链。安装版 Spore 2.2.0j 的 `HowitzerRangedAttackGoal.tick` 在射程外或暂时失去射线时每 Tick调用 `navigation.moveTo(target)`；当前整合包未加载 AI Fix，因此其替代 Goal 没有接管这条路径。

## 已实现

- 灾厄实体目标寻路在进入 `createPath` 前执行请求仲裁：同 Tick重复请求合并，活动路径、直线接近租约和失败冷却可复用。
- 成功请求按实体 UUID 稳定错峰到 10–20 Tick；目标移动超过 4 格、目标变化、新受击或卡住恢复立即解除等待。
- 标准、Hybrid 和 Underground 三类灾厄导航均接入同一运行时；朽翼魔龙继续按现有配置排除。
- 新增路径尝试、复用、同 Tick抑制、目标突变、卡住解除、成功和失败计数。
- Howitzer 每 200 Tick 的约 38,000 格矿物搜索改为只读已加载区块的增量任务：单体 1,024、全局 4,096 方块/Tick、500 微秒硬预算。
- Howitzer 开火前可燃方块统计取消临时 `ArrayList`，使用可变坐标和已加载区块直读。
- 共享目标感知的 nearest 路径直接遍历共享帧，不再为每个观察者额外创建候选列表。
- `FungalWorkScheduler` 新增侵蚀/卷须合计 900 微秒硬上限、16 位置时间检查粒度和双队列交替优先级。
- 修复隔离冒烟脚本中的无效 `spore:grakensenker` ID，并让灾厄探针运行超过 200 Tick，确保实际触发 Howitzer 后台扫描。

## 配置

新增 `[refactor.calamityNavigation]` 的 `pathRequestReuse`、`repathMinTicks`、`repathMaxTicks`、`repathTargetMoveDistance`；新增 `[refactor.howitzer]` 的矿物扫描与可燃统计开关和预算；新增 `[refactor.foliage] schedulerTimeBudgetMicros`。全部配置均有紧邻中文注释，并已写入正式整合包配置。

## 验证

- `clean test build --no-daemon`：通过。
- 新增单元测试：同 Tick重复请求、活动路径复用、目标突变/卡住解除、失败冷却、256 实体抖动分布、矿物游标边界。
- `run-runtime-smoke.ps1 -ModuleSet spore -ProbeCalamity`：通过。
- `run-runtime-smoke.ps1 -ModuleSet sporesrp -ProbeCalamity -Aggressive`：通过；Howitzer、HowitzerRangedGoal、Calamity、Hybrid、Underground Mixin 均在真实 Forge 47.4.22 服务端完成转换。
- 未发现 Mixin、链接或实体 Tick异常。隔离环境中缺少 Create/Farmer's Delight 时产生的配方错误属于既有测试依赖噪声。

## 未完成的动态验收

尚未在用户提供的同一正式存档视角重新采集 Spark，因此不能把隔离冒烟结果表述为实际 MSPT 降幅。下一步应固定实体数、位置、目标与视距，分别采集 180 秒完整采样和 `>60 ms` 慢 Tick样本，重点比较 `HowitzerRangedAttackGoal.tick`、`CalamityPathNavigation.createPath` 与 `ExpPathFinder.findPath`。

## 版本

- 基线提交：`87f9491b43ae5e790e7ca1c557429ef1a07af5d1`
- 实现提交：本报告所在提交（提交完成后以仓库 HEAD 为准）
- 发布版本：`1.0.12`
