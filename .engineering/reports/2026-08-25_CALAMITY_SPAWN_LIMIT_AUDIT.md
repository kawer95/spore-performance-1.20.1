# Spore 灾厄生成上限审计

## 结论

原版 Spore 与 sporesrp 都没有“灾厄总数”或“每种灾厄”这一层的跨来源上限。原版只有感染生物类别的通用 `mob_cap`；sporesrp 的支援、Proto/完整心智技能和 Boss 池各自检查条件，没有共享计数器。

## 原版 Spore 证据

- 源码 `Spore_2.0_1.20.1-master/src/main/java/com/Harbinger/Spore/Core/Sentities.java:46` 只创建了 `INFECTED`、`ORGANOID`、`EXPERIMENTS` 三个类别。
- `sieger`、`gazenbreacher`、`hindenburg`、`howitzer`、`stahl`、`hohlfresser`、`leviathan` 等灾厄注册在 `Sentities.java:455-507` 的通用 `INFECTED` 类别中，没有独立的 Calamity 类别计数。
- 安装版 `spore_1.20.1_2.2.0j.jar` 的 `SConfig$Server` 只有 `mob_cap`（默认 40）作为感染类别上限；它不能区分普通感染体和灾厄，也不能约束直接 `addFreshEntity` 的技能/结构召唤。

## sporesrp 证据

- `sporesrp-common.toml` 的 `[pools].boss` 包含 9 种灾厄：`sieger`、`howitzer`、`stahl`、`hohlfresser`、`gazenbreacher`、`kraken`、`leviathan`、`hindenburg`、`verfall`。
- `[support]` 以阶段概率、等级倍率和每个来源的冷却召唤支援；`supportCooldownSeconds` 不是全维度灾厄总量限制。
- Proto/完整心智部分的 `largePoolThreshold`、Boss 检测范围和每次生成数量只控制单个技能是否触发，不会和支援系统共享灾厄计数。
- 对 `sporesrp-1.7.2.jar` 的反编译确认 `SupportSystemHandler`、Proto/FullHivemind 技能通过实体创建/加入世界完成召唤，没有全局灾厄计数器。

## 本次实现

- 在 `SporePerformance` 的服务端实体加入事件最早阶段增加灾厄计数器，统一覆盖自然生成、sporesrp 支援、Proto/完整心智技能、结构召唤和命令/刷怪蛋生成。
- 新增 `[limits.calamity]`：
  - `maxTotal = -1`：单个维度内所有灾厄总数，`-1` 不限制。
  - `maxPerType = -1`：单个维度内同一种灾厄上限，`-1` 不限制。
- 存档已有实体允许加载并计入后续容量；不会删除、传送或修改旧实体。
- 实体离开维度、死亡移除、换维和服务器停止都会释放计数；状态命令显示每维度总数和按类型计数。
