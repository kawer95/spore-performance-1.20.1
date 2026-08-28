# Spore Performance 客户端第二轮优化交付记录

## 新 Spark 基线

- 记录：`profile-2026-08-23_20.45.48.sparkprofile`
- 最近 1 分钟 MSPT：平均 42.696 ms，中位 40.256 ms，P95 57.379 ms，最大 612.736 ms。
- 最近 5 分钟 MSPT：平均 45.212 ms，P95 63.810 ms，TPS 约 19.79。
- 记录中有 1,802 个实体，其中 694 个掉落物、133 个 FallingBlock、100 个 `spore:bile`。这些数量是独立的整合包压力，本轮没有通过删除实体掩盖开销。
- 本轮直接处理的已证实热点包括 Sona `InfectionManager.canChunkInfection`、Spore/Sona 客户端感染画面及高复杂度 Spore 模型附加层。

## 已实现

- 按 Calamity、Organoid、Hyper、Proto 分类的效果层距离剔除与动画 LOD 开关。
- Gazenbrecher、Sieger、verwahrung、Howitzer 安装版模型根签名计划；不匹配时安全回退整模。
- 动画缓存增加 SynchedEntityData 版本失效，避免技能状态变化继续复用旧姿势。
- Sona 覆盖层与后处理同帧共享感染可用性、平均感染度和颜色采样。
- Sona 最多 108 个 GUI 方块合并为单次顶点提交，固定粒子种子预计算。
- 可选 Sona 覆盖层几何 LOD 命中时完全跳过 CPU 顶点生成；可选半分辨率颜色后处理。
- Sona `canChunkInfection` 按 Level、gameTime 做线程安全同 Tick 缓存。
- Sona Mixin 使用安装版完整方法描述符门控；缺失或版本漂移时单项关闭。
- 客户端指标补齐模型计划、分类动画、分类效果层、Sona 批次/采样/分辨率与兼容状态。

## 验证与部署

- `clean check build --offline`：成功。
- 单元测试：33 项，0 失败，0 错误。
- JAR 包含有效 `pack.mcmeta`、`META-INF/mods.toml`、Mixin 配置与 refmap。
- 部署 SHA-256：`407D20F058FCF30D98BF3B114EC303A0799D3E2FCAF390470F45C62307EB7787`。
- 正式位置：`E:\斗蛐蛐\.minecraft\versions\国潮红师2\mods\spore_performance-1.0.0.jar`。
- 旧 JAR 备份：`E:\斗蛐蛐\.minecraft\versions\国潮红师2\mods\deployment-backups\2026-08-23_220910_client_round2\spore_performance-1.0.0.jar`。

## 仍需游戏内验收

- 重启后检查 `/sporeperformance status` 与 `/sporeperformanceclient status`。
- 默认安全配置连续运行十分钟，检查 HIGH 级 OpenGL 消息、姿势污染和发光部件缺失。
- 按单项开关执行 Spark A/B；视觉激进项默认关闭，不能以尚未运行实机场景代替性能验收。
