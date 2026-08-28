# Spore Performance 真菌装饰距离剔除工作总结

## 目标与范围

- 在优化附属中为此前诊断资源包隐藏的 13 种 Spore 高密度装饰方块加入客户端距离剔除。
- 默认模板关闭；当前“国潮红师2”实例先行开启，显示距离 32 格。
- 不伪造客户端方块状态，不修改服务器世界、碰撞、光照、选取或 Spore 行为逻辑。

## 实现

- 新增可选 Embeddium 0.3.31 Mixin，在 `BlockRenderer.renderModel` 区块网格编译入口读取方块状态和世界坐标。目标方块超出观察点距离时只取消模型写入。
- 目标包括：`biomass_bulb`、`blomfung`、`bloomfung2`、`exploding_lump`、`fang_lump`、`fungal_clamp`、`fungal_stem_sapling`、`fungal_stem`、`fungal_stem_top`、`growth_mycelium`、`growths_small`、`remains`、`bile_lump`。
- 区块工作线程只保存包含目标方块的 `SectionPos` 长整数键，不持有 Level、实体或方块实体引用；目标注册表解析为一次性不可变 Block 集合。
- 玩家移动超过配置步长后，只检查已经确认含目标方块的区段；旧/新观察点处跨越显示球边界的区段进入去重队列。主线程按每 Tick 配置预算调用 `LevelRenderer.setSectionDirty`，不重建整个视距。
- 区块卸载清理对应索引；世界卸载清空全部坐标和队列。开关或距离变化会按预算重建已知目标区段，实现热重载恢复。
- Embeddium 缺失或 `renderModel` 签名漂移时 Mixin 插件 fail-closed，并在 `/sporeperformance status` 中显示 `SKIPPED/INCOMPATIBLE`。

## 配置

客户端新增 `[localRendering]`，每项采用“一行中文注释紧邻一行配置”：

- `fungalDecorationDistanceCull=false`：发行默认关闭。
- `fungalDecorationRenderDistance=32`。
- `fungalDecorationCameraStep=2`。
- `fungalDecorationSectionRebuildsPerTick=8`。

当前实例 `E:\斗蛐蛐\.minecraft\versions\国潮红师2\config\spore_performance-client.toml` 已设为开启。全隐藏资源包已从 `options.txt` 启用列表移除，并可恢复地重命名为 `Spore诊断-隐藏高密度真菌地表_DISABLED`；未删除资源。

## 修改文件

- `build.gradle`：加入当前 Embeddium JAR 的 compile-only 开发依赖。
- `src/main/java/com/arxyt/sporeperformance/client/FungalDecorationCulling.java`：索引、距离判断、边界分类和限额重建。
- `src/main/java/com/arxyt/sporeperformance/mixin/OptionalEmbeddiumBlockRendererMixin.java`：可选网格编译 Hook。
- `PerformanceConfig.java`、Mixin 插件/清单、`mods.toml`、默认客户端配置、README、运行签名脚本。
- `FungalDecorationCullingTest.java`：区段完全显示、完全剔除、跨边界和负坐标测试。

## 验证结果

- `clean test jar`：通过，共 17 项测试。
- `verifyMixinPackageSafety`：通过；新增 Mixin 未引入嵌套 Mixin 或非法跨包引用。
- 当前安装 Embeddium 0.3.31 的 `BlockRenderer.renderModel` 与 `BlockRenderContext.state` 签名：通过。
- 三件套完整隔离专用服务器启动：通过，证明客户端软依赖未污染服务端加载。
- JAR 资源结构：包含合法 `pack.mcmeta`、`mods.toml`、Mixin 清单及两个新增类。
- 构建与部署 JAR：`spore_performance-1.0.0.jar`，174478 字节，SHA-256 `7FF0BDBC67E95BB2C47D02D8888B21146B67CA7CB93177CCDF3128786BEAF422`；源码制品与 mods 目录制品哈希一致。

## 风险与人工验收

| ID | 作用面 | 失效表现 | 当前保护/回退 | 状态 |
| --- | --- | --- | --- | --- |
| SP-CULL-001 | Embeddium 版本签名 | 客户端 Mixin 不应用 | 预变换 ASM 签名检查，独立 fail-closed | MITIGATED |
| SP-CULL-002 | 移动时区段重建 | 32 格边界短暂延迟或出现模型弹入 | 2 格观察点步长、每 Tick 8 区段预算，可配置或关闭 | OPEN |
| SP-CULL-003 | 全隐藏诊断资源包 | 近处模型仍为空 | 已移出启用列表并重命名保留 | MITIGATED |
| SP-CULL-004 | 当前客户端仍运行旧 JAR | 本轮客户端 Hook 尚未真实加载验证 | 必须完整退出并重启后检查日志与游戏画面 | OPEN |

当前 Minecraft 进程在部署时仍运行旧 JAR，因此不能把编译、签名和服务端启动当作客户端实景验收。重启后应检查 32 格内模型恢复、32 格外消失、移动边界无严重卡顿，并搜索 `OptionalEmbeddiumBlockRendererMixin`、`Mixin apply failed`、`InvalidInjectionException`。

## 回退与凭据

- BASE 快照：`E:\斗蛐蛐\.minecraft\脚本备份\SporePerformance-1.20.1_20260823-180832_fungal-distance-cull`。
- 旧部署 JAR：`E:\斗蛐蛐\.minecraft\脚本备份\deployed\spore_performance-1.0.0_20260823-181449.jar`。
- 实现 DIFF：`.engineering/diffs/2026-08-23_SP1_FUNGAL_DECORATION_DISTANCE_CULL.diff`。
- 本项目位于上级混合 Git 工作树且自身未作为独立跟踪单元，故本工作单元以 BASE 快照和机械 DIFF 为变更凭据。
