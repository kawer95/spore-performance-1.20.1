# Spore Performance：群体感知缓存增量

## 工作单元

- ID：`2026-08-23_SP1_GROUP_SENSING`
- 非 Git BASE：`2026-08-23_SP1_INITIAL_IMPLEMENTATION` 的源文件快照。
- 机械 DIFF：`.engineering/diffs/2026-08-23_SP1_GROUP_SENSING.diff`。

## 实现

为 Spore `LocalTargettingGoal.Targeting` 增加默认关闭的激进共享邻居快照：同维度、同目标、同候选类型在五个游戏 Tick 内复用一次 `getEntitiesOfClass` 查询。返回前重新检查实体存活、当前 AABB 和原 Predicate；缓存只保留实体引用最多五 Tick，停服时清空。

该项由 `[aggressive].enabled` 和 `groupSensingCache` 双重控制，默认不改变现有行为。它只覆盖已关联感染体的目标传播，首次查询仍立即执行。

## 验证

- `clean build --no-daemon` 通过。
- 新 JAR SHA-256：`94DAEA32F2D0F17D6916406FB2F82D38870E63C72B4FEAB8831E9867A6C3F793`。
- 注入点以安装 Spore JAR 的 `LocalTargettingGoal.Targeting` 中 `Level.m_6443_` 调用核验。
- 真实运行栈测试仍受 ForgeGradle 外置 Spore JAR 映射冲突影响，详见初始实现总结的 SP-001 / SP-005。

## 风险

SP-004 仍为 OPEN：不同感染体的 32 格邻域在五 Tick 窗口内可能略有差异；缓存结果会重做当前 AABB 过滤，但不补全第一次查询范围外的候选。这是显式激进模式下可接受的响应时机变化，Spark 行为回归必须覆盖受击传播与撤退。
