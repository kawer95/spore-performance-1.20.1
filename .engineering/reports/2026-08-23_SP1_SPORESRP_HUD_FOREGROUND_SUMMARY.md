# Spore Performance：sporesrp HUD 单次前景渲染

## 工作单元

- ID：`2026-08-23_SP1_SPORESRP_HUD_FOREGROUND`
- 目标：消除 sporesrp HUD 在每个 Overlay Post 阶段的重复绘制，并在背包/界面打开时保持 HUD 清晰地显示在模糊层前方。
- 非目标：不修改 sporesrp 服务端数据、演化点计算、材质、坐标、文字内容或公开 API。
- BASE 快照：`../.codex-backups/2026-08-23_SP1_SPORESRP_HUD`。
- 机械 DIFF：`.engineering/diffs/2026-08-23_SP1_SPORESRP_HUD_FOREGROUND.diff`。

## 证据与实现

安装版 sporesrp `1.7.2` 的 `HUDOverlay.onRenderGui(RenderGuiOverlayEvent.Post)` 没有检查 `event.getOverlay()`，因此 Forge 每发布一次 Overlay Post 就重复绘制整套材质、点数、冷却和难度文字。2026-08-23 的客户端 Spark 样本中，该方法达到 Render thread 的 `16.45%`。

新增客户端兼容补丁：

- `OptionalSporeSrpHudMixin` 在接管器可用时取消 sporesrp 原事件总线调用；签名不匹配或反射句柄不可用时不取消原 HUD。
- `SporeSrpHudForeground` 使用一次性缓存的 `MethodHandle` 调用经过 Mixin 转换的原 HUD 方法，热路径不执行类查找或方法搜索。
- 普通游戏画面只在 `VanillaGuiOverlay.HOTBAR` Post 阶段绘制一次。
- 打开背包或其他游戏 Screen 时跳过后景 HOTBAR 绘制，改在 `ScreenEvent.Render.Post` 绘制一次，位于背景模糊和 Screen 内容之后。
- ThreadLocal 旁路只允许接管器的直接调用进入原方法，避免放开 Forge 的重复事件调用。
- `HudRenderStagePolicy` 保证同一帧只选择 HOTBAR 或 Screen 前景中的一个阶段。

客户端配置新增且默认开启：

```toml
[compat.sporesrp]
# 接管 sporesrp HUD，只在热键栏阶段绘制一次，避免每个 Overlay 阶段重复绘制。
relocateHudToHotbar = true
# 打开背包或其他界面时，在屏幕最前层绘制 HUD，避免被背景模糊处理。
renderHudAboveScreens = true
```

两项均支持 Forge 配置重载。关闭 `relocateHudToHotbar` 会恢复 sporesrp 原始事件处理；关闭 `renderHudAboveScreens` 会保留 HOTBAR 阶段并允许 Screen 按原层级覆盖它。

## 修改范围

- 新增客户端接管器、渲染阶段策略、可选 Mixin 和三项策略单元测试。
- 扩展客户端配置、Mixin 插件签名检查、状态命令、默认配置、运行签名脚本与 README。
- 正式客户端配置已补入中文逐项注释。
- 不包含 sporesrp JAR，也不产生对其类的硬链接；目标方法通过缓存 MethodHandle 调用。

## 验证

- `clean test jar --no-daemon -Dnet.minecraftforge.gradle.check.certs=false`：通过。
- JUnit：HOTBAR、Screen 前景、关闭前景三种状态均通过，验证两阶段互斥。
- `verifyMixinPackageSafety`：通过。
- 安装 JAR 签名：`HUDOverlay::onRenderGui` 及既有十项 Spore/sporesrp 方法全部通过。
- 专用服务器启动矩阵：仅 Spore、Spore+AI Fix、Spore+sporesrp、三件套完整加载均通过；无客户端类泄漏、缺类、Mixin 注入或链接错误。
- 新 JAR：`spore_performance-1.0.0.jar`，`164485` bytes，SHA-256 `6C4D478E91E12B57AAF4086C0320E081DBD92AEC36E445230F264A7240EE83A8`。
- 前一部署 JAR SHA-256：`9807A006014D76FF8B99541DFC5F0B1E2F15252AB2E721A4A8C6FD90274038D9`。
- 构建产物与正式 mods 中部署产物哈希一致。

## 人工验收与风险

- `SP-HUD-001 OPEN`：当前游戏 Java 进程启动于新 JAR 部署之前，必须完整重启客户端后验证。检查正常 HUD 每帧只出现一次，背包、容器、暂停界面中 HUD 清晰且不重复。
- `SP-HUD-002 OPEN`：ModernUI、Oculus、ImmediatelyFast、AcceleratedRendering 和整合包模糊处理的组合顺序只能由真实客户端视觉验收确认。若某个 Screen 在 Post 之后再次做模糊，应把该具体 Screen/模组登记后增加更晚的兼容 Hook，不回退为全 Overlay 重复绘制。
- `SP-HUD-003 OPEN`：重启后使用相同场景再次采样 60 秒客户端 Spark；验收目标是 `HUDOverlay.onRenderGui` 从 `16.45%` 显著下降且调用只来自接管阶段。

## 回退

优先把 `relocateHudToHotbar=false`，即可不启用接管并恢复 sporesrp 原行为。完整二进制回退可从工作单元 BASE 快照恢复旧 JAR和旧客户端配置；不要替换或修改 sporesrp 原 JAR。
