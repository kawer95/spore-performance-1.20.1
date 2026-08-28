# 全灾厄转圈与导航卡死修复

## 落地范围

- 新增每 `ServerLevel` 一份的 `CalamityNavigationRuntime`。状态只保留 UUID、坐标快照、节点索引、恢复阶段和退避时间；实体离开、区块卸载、维度卸载及停服时均清除。
- 覆盖标准 `CalamityPathNavigation`、海妖的 `HybridPathNavigation` 与地底吞噬者的 `UndergroundPathNavigation`。海妖水下推进、地底吞噬者钻地移动、飞艇飞行推进和钢铁屠夫攀爬控制没有被替换。
- `Verfalldrachen` 按类名及配置排除，不进入运行时、朝向仲裁、恢复或路径缓存。

## 修复内容

1. `SmoothLookControl` 继续更新头部、视线和俯仰，但在非技能状态下不再覆盖移动执行器刚写入的身体 Yaw 与身体插值值。
2. 标准/Hybrid 导航原有的 `isStuck()` 无条件 `recomputePath()` 被取消；运行时以节点索引和体型归一化位移判断无进展。
3. 无进展处理顺序：一次受控重算、一次确定性静态替代点/动态侧向接入点、再以 20/40/80 Tick 退避。退避时仅停止导航请求和身体转向，不跳过受击、物理、攻击、方块破坏或技能。
4. 标准、Hybrid 和地下导航不再把“创建路径失败”伪装成 `moveTo` 成功。
5. 路径缓存从单一维度地形版本改为经过的 16×16×16 区段版本快照；无关方块变更不会清空同维度所有路径。实体目标和固定 `BlockPos` 目标使用不同缓存键。

## 配置与诊断

`[refactor.calamityNavigation]` 默认全部开启，且每项紧邻中文注释：单一 Yaw 写入者、进展恢复、固定坐标缓存、区段失效、20 Tick 阈值、4 格替代点和 20/40/80 Tick 退避。

`spore-performance-calamity-trace.jsonl` 新增 `navigation_intent`、`navigation_yaw_owner`、`navigation_progress`、`navigation_yaw_without_progress`、`navigation_stuck`、`navigation_recovery_*`、`navigation_backoff*` 等内部运行时记录。`/sporeperformance status` 会显示各灾厄类型的卡路、重算、替代点、退避和“低位移高转角”计数。

## 验证

- `check jar`：通过；包含配置注释测试、导航策略单元测试及 Mixin 包安全检查。
- `verify-runtime-signatures.ps1`：通过。
- 完整三件套隔离服务端启动：通过；数据包实际构造 Gazenbreacher、Kraken、Hindenburg、Leviathan、Sieger、Stahl、Howitzer、Hohlfresser 和 Verfalldrachen，未出现 Mixin、链接或类加载错误。

## 部署

- 正式 JAR：`E:\斗蛐蛐\.minecraft\versions\国潮红师2\mods\spore_performance-1.0.0.jar`
- SHA-256：`AC390FDAE9CD6E3A48C1FFBD091F9C6BF1E765F4F956BD0D8B7102BB210ECFBA`
- 可恢复的部署前备份：`E:\斗蛐蛐\.minecraft\脚本\.codex-backups\2026-08-24_SPOREPERFORMANCE_CALAMITY_NAVIGATION\spore_performance-1.0.0.before.jar`
