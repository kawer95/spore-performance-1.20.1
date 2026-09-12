# SporePerformance 1.0.13：Follow 与普通感染体性能修复

## 依据

慢 Tick Spark `F0inZYj4mw` 证明 1.0.12 的 Howitzer 修复有效：更重负载下 Howitzer Tick 从旧样本 5.76% 降至 0.71%。本版本保留该实现，处理剩余的 `FollowOthersGoal.tick/reusePartnerPath`、普通 Infected `aiStep` 和 `FungalPathService`。

## 根因与修复

- `FollowOthersGoalMixin` 的共享走廊分支在节流判断前直接调用 `navigation.moveTo(waypoint)`，因此绕过了 `FollowPathThrottle`。
- `FollowPathThrottle` 把 `navigation.isDone()` 当成立即重建许可；短路径结束后，Spore 原 Goal 的周期与 stuck 分支会反复进入 `createPath`。
- 现在共享走廊和直接目标路径统一先经过 40 Tick＋UUID 相位租约；路径结束不再提前突破租约，伙伴变化、移动超过 2 格和失败退避仍可触发正确重试。
- 伙伴最近候选直接遍历共享感知帧，不再为每只观察者构造候选 `ArrayList`。
- 无目标、未受击、非乘客的 Basic/Evolved/Hyper 感染体把完整 GoalSelector 启动/清理扫描错峰到每 4 Tick；运行中的 Goal 改走 `tickRunningGoals(true)`，攻击、跟随、移动、目标失效和受击仍逐 Tick。
- FungalPathService 的主线程粗走廊快照从最多 32 个/维度/Tick降为 1 个，并增加 750 微秒的“下一个快照”提交边界，避免 Follow 群体同时请求时集中复制大量方块。

## 配置与计数

- `[refactor.ai] idleSelectorStagger=true`、`idleSelectorInterval=4`。
- `[refactor.navigation] snapshotBudgetPerTick=1`、`snapshotTimeBudgetMicros=750`。
- 新增 `ai.follow.refactor_path_reused_or_deferred`、`ai_refactor.perception.nearest_matching_without_list`、`ai_refactor.selector.idle_full_ticks(_deferred)`、`ai_refactor.path.snapshot_tick_nanos` 和 `snapshot_time_budget_hit` 计数。

## 验证

- `clean test build --no-daemon` 通过；Follow 回归测试确认已结束路径仍等待租约、伙伴显著移动立即重算、失败保持 20/40/80 退避。
- Spore＋sporesrp＋激进配置的隔离 Forge 47.4.22 服务端通过，灾厄探针运行超过 200 Tick，无 Mixin、链接或实体 Tick异常。
- 冒烟工具现会在 `server.properties` 缺少端口项时追加独立端口，避免与其他开发服务器冲突。

## 动态验收边界

隔离测试不包含正式世界中的实体密度，因此仍需相同世界状态的 180 秒 Spark。验收重点是 `FollowOthersGoalMixin.reusePartnerPath`、`PathNavigation.createPath`、普通 `Infected.aiStep` 和 `FungalPathService.tick`；不得把随机实体数量变化当成收益。

## 版本

- 基线提交：`6059d7e`
- 发布版本：`1.0.13`
