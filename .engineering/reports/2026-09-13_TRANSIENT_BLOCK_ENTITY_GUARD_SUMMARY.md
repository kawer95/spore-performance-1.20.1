# SporePerformance 1.0.14：地形块实体累积防护

## 现场证据

Spark `MkjaSwVKJW` 中总实体从上一份的 1,652 增至 3,608；其中 vanilla `falling_block` 为 2,633，单一区域达 2,086。FallingBlock 自身 Tick 仅约 0.08%，但大量实体扩大了 Prion 全实体碰撞候选和 Accelerated Recoiling 推挤集合。

## 审计结论

- `spore_performance:stahl_rising_block` 是 Stahl 落地视觉实体，原本寿命约 22–46 Tick；它不是大量 vanilla `falling_block` 的直接类型来源，但连续落地仍可能造成短时峰值。
- Spore 中能创建 vanilla FallingBlockEntity 的主要来源包括 FoliageSpread 木材转换、Hohlfresser `tryAndCrumbleBlocks`、Howitzer/Calamity 地形效果、ThrownBlockProjectile、CorpseEntity 和部分 Hyper/灾厄抛掷逻辑。
- FoliageSpread 和 Hohlfresser 是最值得现场归因的候选；新版在生成时记录来源，无需每 Tick堆栈采样。

## 实现

- 在 `FallingBlockEntity.fall` 方法头部执行预约配额，先于 vanilla 移除原方块。配额拒绝时静态方法直接返回，不产生实体。
- Hohlfresser 是已先移除方块再调用 `fall` 的例外；拒绝时只恢复该来源的原方块，避免静默地形丢失。
- 新增 `[limits.fallingBlocks]`：FallingBlock 默认全局 512、每区块 96、每来源 256、TTL 200 Tick；Stahl rising block 默认全局 256、每区块 64、TTL 60 Tick。
- 加载旧存档时，未触发 Join 事件的历史 FallingBlock 会在首次 Tick纳入配额；超额实体立即 discard，允许已加载区块快速从数千实体回落到上限。
- 维度卸载、实体离开和 20 Tick遗留 UUID 清扫都会释放计数；运行时不持有实体或旧 Level 强引用。
- `/sporeperformance status` 输出每维度 Falling/Rising 数量、待激活预约、区块数、来源枚举和拒绝计数；metrics 输出来源拒绝和清理计数。

## 验证

- `clean test build --no-daemon` 通过。
- Spore＋sporesrp＋激进配置的隔离 Forge 47.4.22 冒烟服务器通过；`FallingBlockEntityMixin` 在真实 vanilla FallingBlockEntity 目标上显示 `ACTIVE`，并由 datapack 同时构造普通 FallingBlock 和实际沙块下落场景。
- 其余既有 Create/Farmer's Delight 配方警告来自隔离环境缺依赖，不属于本变更。

## 后续现场验证

重启后执行 `/sporeperformance status`，记录 `Transient blocks` 行的 `sources`。若 FOLIAGE 或 HOHLFRESSER 持续占主导，再在不改战斗伤害的前提下为该单源增加更低的独立每 Tick生成预算；不得删除已验证有效的 Howitzer/Follow 优化。

## 版本

- 基线提交：`6777a97`
- 发布版本：`1.0.14`
