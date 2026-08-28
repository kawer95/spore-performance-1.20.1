# Spore AI Fix 吸收与排除审计

## 纳入 AI 重构的修复

| AI Fix 原逻辑 | 新实现 | 性能处理 |
|---|---|---|
| `CalamityInfectedCommand.Targeting` | `AiFixCalamityCommandMixin` | 取消32格通用世界扫描，使用维度实体索引；保留目标、SearchArea和有效性语义 |
| `LocalTargettingGoal`/灾厄群体命令 | `FungalGroupCoordinator` | 事件驱动传播和同Tick候选帧，稳定实体ID顺序 |
| `SearchAreaGoal`继续条件与3格抵达阈值 | `AiFixSearchAreaGoalMixin` | 直接状态判断，无新增轮询 |
| 感染体搜索命令NBT持久化 | `AiFixInfectedSearchPersistenceMixin` | 兼容原`SporeFix*` NBT键，AI Fix装卸不丢命令 |
| Hyper回巢/随机漫步覆盖外部命令 | `AiFixHyperNestGoalMixin`、`AiFixHyperRandomStrollMixin` | 在创建随机路径前短路，已有路径和搜索命令优先 |
| Hinderburg对空中目标失效 | `AiFixHinderburgTargetMixin` | 用共享索引和普通循环替换世界查询、Stream、Comparator |
| Hinderburg错误启用近战 | `AiFixCalamityGoalGuardsMixin` | `canUse/canContinue`在寻路前短路 |
| Howitzer远距仍启用近战和Leap | `AiFixCalamityGoalGuardsMixin`、`AiFixLeapGoalMixin` | 不创建无效近战路径；保留AI Fix最终弹道状态机 |
| Stahl Leap失控、落点漂移与错误恢复 | `AiFixStahlLeapGuardMixin`、`AiFixStahlmorderControlMixin` | 12–34格有效距离；目标附近落点、上升阻尼、下降转向和80 Tick超时；落地AOE候选复用共享索引 |
| Stahl近战因路径结束中断、假攻击和移动目标漏判 | `AiFixStahlMeleeGoalMixin` | 保持有效目标至96格；9 Tick延迟命中、1.35倍末帧容差；AOE候选复用共享实体索引并在伤害前精确复核 |
| Stahl数值及完整落地表现 | `AiFixStahlmorderControlMixin`、`PerformanceEntities` | 移速0.34、斩击45伤害、攻击形态概率、虚弱/缓慢/回血、12点距离衰减落地伤害与击退；完整粒子和短寿命升起方块实体/渲染器 |
| Grakensenker漏斗逐Tick实体扫描 | `AiFixGrakensenkerWorkMixin` | 范围力查询10 Hz；目标、移动和攻击仍20 Hz |
| Hybrid/Water Calamity节点错误读取`BlockPos.ZERO` | `AiFixSwimmingNodeMixin`、`AiFixWaterCalamityNodeMixin` | AI Fix缺失时独立接管；存在时跳过，避免Overwrite冲突 |
| Howitzer最终弹道LOS/轨迹选择 | `OptionalHowitzerMixin`＋轨迹预算 | AI Fix存在时缓存同Tick结果并限制替换目标阶段的新轨迹探测 |
| Calamity/Hybrid空路径假成功和stuck循环 | 共享`FungalPathService`导航适配 | 返回真实失败；路径请求缓存、合并、异步粗走廊和预算 |

## 保留在 AI Fix、未复制的战斗内容

以下属于尚未纳入的其他实体技能或玩法：Howitzer/Hinderburg/Gazenbrecher/Sieger的非AI弹道与射速改写、Hohlfresser冲锋手感、Grakensenker鱼叉散布、Leviathan齐射与部位伤害、ThrownTumor效果、Storm Fortress、Mournful Roar。Stahl是明确例外：其AI、数值和完整落地表现均按用户要求移植。

## 明确排除

- `Immortal*`、`PersistentEntity*`、`HohlfresserRemovalGuard`、`LeviathanRemovalGuard`。
- 不朽君王龙头翼保护、强制复活、实体列表回填、死亡/移除拦截。
- Forge最终伤害硬上限、全局伤害事件和强制击杀逻辑。
- 永久实体维护审计及其ChunkMap/EntityLookup访问器。

这些逻辑会改变死亡、移除、伤害或存档生命周期，不属于本次AI性能重构。`SporePerformance`不注册、不反射调用，也不复制其NBT或网络协议。

## 兼容策略

- AI Fix存在且带委托插件：AI Fix只跳过自己的四个Stahl Mixin（含内部Invoker），由本附属保持唯一权威；其他永久实体、不朽君王龙和专属技能修复继续留在AI Fix。旧版AI Fix没有委托插件时，本附属反向跳过Stahl三组Mixin，避免冲突。
- AI Fix缺失：本附属独立提供 Stahl Leap、空中转向、落地恢复、可靠延迟近战、数值加强和完整落地特效；升起方块使用本附属自己的实体注册与网络生成包。
- 两个水生NodeEvaluator仅在AI Fix缺失时应用；三组Stahl状态机Mixin在AI Fix缺失或检测到新版委托插件时应用。全部目标经过安装版ASM方法签名门控。

正式栈哈希：`spore_performance-1.0.0.jar`为`FED5B11BCB3AAE9DE4D3DB0EA1CC3EBD44CB5FAE1887CC75672C746B4B6B6F89`；带条件委托的`exhuashan_sporeai_fix-1.0.0.jar`为`05197634451330BD15463CEBDB51F34A6821E9A16F7B059862A8D4177087BB0A`。
