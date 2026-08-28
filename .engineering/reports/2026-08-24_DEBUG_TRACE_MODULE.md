# SporePerformance 全链路调试追踪模块

## 目标

为后续 AI 底层重构、Spore AI Fix 接管和全方位行为回归提供可关联、可限流、可即时查询的诊断证据。调试事件采用 JSON Lines 格式异步落盘，单条事件具备会话、服务器 Tick、维度、线程、实体 UUID、实体类型、坐标、分类、动作、详情和关联追踪 ID。

## 输出与控制

- 日志文件：`logs/spore-performance-debug.jsonl`
- 当前整合包实例：`diagnostics.debugTrace.enabled = true`
- 工程及新实例默认值：`false`
- 写入队列上限：8,192 条
- 当前实例普通事件限速：500 条/秒
- 当前实例实体事件采样：每 1 条记录 1 条
- 内存最近事件：1,024 条
- 严重故障绕过普通采样和普通事件限速，并带完整异常类型及消息。

## 已插桩链路

- 生命周期：运行时创建、实体加入/离开、维度卸载、服务停止清理。
- 感知：共享感知帧创建/复用、候选数量、最近目标结果。
- 威胁与群组：受击传播、Linked 传播、伙伴和领队选择。
- 导航：原生路径缓存、共享走廊、请求合并、队列拒绝、快照提交、异步失败、地形失效、结果应用/丢弃。
- Goal：目标搜索错峰、无效工作抑制、慢实体 Tick。
- 战斗：AOE 候选、Stahl 攻击准备、蓄力、距离/视线拒绝和伤害提交。
- Stahl：跳跃启动/拒绝、落地取消、落地命中、数值效果、客户端方块特效数据。
- 后台任务：菌丘叶簇/卷须任务、感染卷须传播/转化、人口限制、工作令牌和分帧任务预算。
- 兼容：Spore、AI Fix、sporesrp 的探测、签名和接管结果。

## 管理员命令

- `/sporeperformance debug status`
- `/sporeperformance debug recent [1-50]`
- `/sporeperformance debug watch <uuid>`
- `/sporeperformance debug unwatch <uuid>`
- `/sporeperformance debug clearwatch`
- `/sporeperformance debug reset`

`watch` 用于只追踪特定实体，适合复现单只灾厄、菌丘或卷须的错误；`reset` 在每个测试场景开始前清空会话内计数和最近事件，磁盘日志继续保留以便跨场景审计。

## 性能保护

- 日志在专用守护线程写盘，游戏线程只组装事件并尝试进入有界队列。
- 所有类别均可独立关闭；总开关关闭时不会启动写入线程，也不会产生磁盘写入。
- 普通事件受每秒上限和实体采样控制；队列满时丢弃普通诊断事件而不阻塞服务器 Tick。
- 不持有 `ServerLevel` 或实体强引用，日志只保存不可变标量与字符串。
- 详细追踪用于行为验证和故障复现；正式 Spark 性能基线应关闭总开关，或把 `sampleEveryN` 调至 5–10。

## 验证结果

- `clean build`：通过。
- Spore + AI Fix + sporesrp 完整启动矩阵：通过。
- Calamity/Stahl 探针：通过。
- 开启追踪时成功生成并解析结构化 JSONL，类别覆盖生命周期、感知、群组、导航、Goal、后台与兼容。
- 关闭追踪的对照启动中日志文件长度不变，确认默认关闭路径无写盘。
- 部署 JAR SHA-256：`6C8648255B043315C6BCB5FAB5BC70CF5EB7340E08B1F6B5D2AB0AF0DE3980AD`。

## 建议测试流程

1. 进入测试存档后执行 `/sporeperformance debug reset`。
2. 单实体问题先执行 `/sporeperformance debug watch <实体 UUID>`；群体行为测试不设置 watch。
3. 完成复现后执行 `/sporeperformance debug recent 30` 快速确认最后状态。
4. 同时保存 `spore-performance-debug.jsonl`、`latest.log`、崩溃报告和 Spark 链接。
5. 正式测量 MSPT 前将 `diagnostics.debugTrace.enabled` 改为 `false`，重启后采样，避免把诊断开销算入优化结果。
