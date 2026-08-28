# SporePerformance 1.0.0 服务端重构落地记录

## 本次落地

- `HohlMultipart` 服务端路径改为 `Entity.baseTick` 加父实体有效性、命中箱同步、死亡/移除和分体近战处理；近战候选来自维度级真菌索引。
- 无目标/未受击的菌丘和扎根 `GastGeber` 进入轻量 Tick；工作令牌在进入完整 AI 前生效，旧存档实体不删除。
- Follow 伙伴使用维度级群组、尺寸感知抵达半径、近距直接转向和共享走廊，确实受阻时才创建本地路径。
- Busser 按变体裁剪互斥 Goal；飞行导航共享碰撞上下文、限制快捷候选并缓存短期结果，到达后停止无效飞行意图。
- 菌丘 Foliage/Tendril 使用惰性邻居读取、预编译感染计划和严格微秒预算游标；任务不强制加载区块、不丢弃队列。
- 女仆 `EntityPowerPoint` 通过维度级玩家索引查询，静止无人时按配置间隔复用物理检查；保持价值、吸引、拾取和寿命语义。
- 本地 Spore 依赖改为 flat repository 后由 ForgeGradle 正确映射，开发服务器不会再以 SRG Spore 字节码调用官方映射类。
- 增加独立 accessor Mixin 配置，使 `HohlMultipart` 可安全调用 `Entity.baseTick`，不让 Mixin 包互相引用。

## 配置

`defaultconfigs/spore_performance-common.toml` 和正式配置均加入计划中的 `refactor.*`、`compat.touhouLittleMaid.*` 段落，注释按“一行注释紧邻下一行配置”生成。SporePerformance 自带掉落物合并仍为 `items.merge.enabled = false`，由 Harium 接管。

## 验证

- `gradle build --no-daemon`：通过（含 `compileJava`、`verifyMixinPackageSafety`、`test`、`reobfJar`）。
- 开发专用服务器 Forge `47.4.22` + Spore `2.2.0j`：成功到达 `Done`，兼容状态正常输出；未加载 AI Fix、sporesrp 和女仆依赖，因此这些分支只完成基础栈启动验证。
- 使用 ForgeGradle 映射测试副本加入 AI Fix `1.0.0`、sporesrp `1.7.2` 和女仆 `1.5.3` 后也成功到达 `Done`；状态显示 AI Fix、sporesrp 和 Howitzer 专项均为 `ACTIVE`，没有 Mixin/类加载错误。
- 开发环境中 Spore 自带的缺失 Create/Farmer's Delight 配方错误仍会被 Forge 记录，但不是本模组错误，也不阻止服务器启动。
- 尚未把正式存档作为动态基准运行 Spark；实体数量、区块加载和玩家位置需要每轮从同一快照恢复后再比较，不能用本次启动结果声称 MSPT 收益。
- 2026-08-25 客户端启动审计发现并修复两项初始化问题：accessor 配置此前缺少 `spore_performance.refmap.json`，导致生产客户端将 `@Invoker("baseTick")` 按未映射名称查找并抛出 `InvalidAccessorException`；补回 refmap 后生成的映射为 `baseTick -> m_6075_()V`。同时将感染映射缓存和远距 AI 配置读取改为“配置未绑定时保留快照、服务器启动后刷新”，避免 `ConfigValue#get` 在 `ModConfigEvent.Loading` 早期抛出 `Cannot get config value before config is loaded`。
- 修复后的隔离 Forge 客户端（Java 17、Forge 47.4.22、Spore 2.2.0j）已完成 Mixin、配置和资源加载，没有 accessor 或配置时序崩溃；资源缺失/配方错误仍是 Spore 或测试环境自身警告。正式 JAR 已重新构建并替换，SHA-256 为 `696AD3F7A6892889E75608A980BA7BE69361C18453D1B1E59CD8E6F51BF7C099`。

## 产物

- 构建产物：`build/libs/spore_performance-1.0.0.jar`
- 正式 JAR 已备份后替换；备份位置记录在交付消息中。
