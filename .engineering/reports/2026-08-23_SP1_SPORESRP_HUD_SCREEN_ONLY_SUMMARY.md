# Spore Performance：sporesrp HUD 仅界面前景显示

## 工作单元

- ID：`2026-08-23_SP1_SPORESRP_HUD_SCREEN_ONLY`
- 目标：普通游戏 HUD/热键栏画面隐藏 sporesrp HUD；打开背包、箱子或其他 Screen 时只在最前层绘制一次，保持清晰且不双重绘制。
- 非目标：不修改 sporesrp HUD 材质、位置、文字、服务端数据或原 JAR；不改动真菌方块距离剔除。
- BASE 快照：`../.codex-backups/2026-08-23_SP1_SPORESRP_HUD_SCREEN_ONLY`。
- 机械 DIFF：`.engineering/diffs/2026-08-23_SP1_SPORESRP_HUD_SCREEN_ONLY.diff`。

## 原因与实现

先前 `HudRenderStagePolicy.useHotbarStage` 在没有 Screen 时恒为 `true`，所以普通游戏画面显示 sporesrp HUD 是旧策略的明确行为，不是前景 Hook 失效。

本次保留既有可选 Mixin 和反射式 fail-closed 适配，只调整接管后的阶段策略：

- 新增客户端配置 `renderHudInGameplay=false`。默认不在普通游戏 Overlay 阶段调用 sporesrp 绘制。
- `renderHudAboveScreens=true` 时，只有 `ScreenEvent.Render.Post` 调用原 HUD 一次，因此它位于 Screen 和模糊层之后。
- 同时开启普通画面显示与 Screen 前景显示时，Screen 仍优先，两个阶段不会在同一帧并存。
- `relocateHudToHotbar=false`、sporesrp 缺失、目标签名漂移或 MethodHandle 解析失败时，适配器不取消原 HUD，继续保持 fail-closed。
- `renderHudInGameplay=true` 可恢复普通画面的单次 HOTBAR Overlay 绘制。

## 修改范围

- `HudRenderStagePolicy`：把游戏 Overlay 是否参与绘制变为显式策略输入。
- `SporeSrpHudForeground`：读取新的普通画面显示开关。
- `PerformanceConfig` 与默认客户端 TOML：新增带中文逐项注释的 `renderHudInGameplay=false`。
- `HudRenderStagePolicyTest`：覆盖默认隐藏、Screen 前景、显式恢复普通 HUD、Screen 优先和全部关闭，共六种组合。
- `README.md`：同步当前有效行为和恢复开关。

## 验证

- Gradle `clean test jar`：通过。
- JUnit：全项目 20 项通过，其中 HUD 阶段策略 6 项。
- `verifyMixinPackageSafety`：通过。
- 安装版运行签名矩阵：Spore、AI Fix、sporesrp `HUDOverlay::onRenderGui` 和 Embeddium 两项签名全部通过。
- 构建制品：`build/libs/spore_performance-1.0.0.jar`，`174744` bytes，SHA-256 `5F8A2EAFBD91CA68616A34163671BE8E8DC29701D33FFAF430D77D23E57351A5`。
- 按任务边界未部署到正式 `mods`，也未改写正在使用的客户端配置，由主 Agent 合并其他并行结果后统一部署。

## 风险、人工验收与回退

- `SP-HUD-004 OPEN`：需要重启真实客户端验证普通游戏画面完全没有 sporesrp HUD，背包、箱子、暂停/聊天等 Screen 中只显示一份且清晰。
- `SP-HUD-005 OPEN`：旧客户端 TOML 中没有新键；Forge 应在加载时补默认值，统一部署时仍应显式写入 `renderHudInGameplay=false` 并核对中文逐项注释。
- `SP-HUD-006 OPEN`：ModernUI 或特定第三方 Screen 若在 `ScreenEvent.Render.Post` 后继续绘制模糊层，需要针对该 Screen 增加更晚的兼容 Hook，不应恢复每 Overlay 重复绘制。

配置回退：设置 `renderHudInGameplay=true` 恢复普通游戏 HUD；设置 `relocateHudToHotbar=false` 完全停用接管并恢复 sporesrp 原行为。代码回退使用本工作单元 BASE 快照。
