# Spore AI 实体与行为矩阵

列含义：`T`=Tick相关覆盖数，`G`=Goal注册数，`Q`=范围实体查询数，`P`=路径请求数，`L`=LOS调用数，`B`=BlockPos立方扫描数。数字来自源码静态扫描；安装版公共入口另见主审计报告。

| 类/家族 | 父类 | T | G | Q | P | L | B | 重构路径 |
|---|---|---:|---:|---:|---:|---:|---:|---|
| Infected | Monster | 1 | 12 | 0 | 0 | 2 | 3 | 感知、威胁、群组、通用查询、导航 |
| Hyper | Infected | 1 | 5 | 0 | 1 | 2 | 1 | 继承公共服务；巢穴/特殊移动保留 |
| Calamity | UtilityEntity | 2 | 2 | 0 | 1 | 0 | 2 | 公共感知＋Calamity导航适配 |
| Organoid | UtilityEntity | 2 | 0 | 1 | 0 | 0 | 0 | 通用查询；器官技能保留 |
| CustomMeleeAttackGoal | Goal | 1 | 0 | 0 | 4 | 2 | 0 | 共享路径＋Goal计数 |
| AOEMeleeAttackGoal | Goal | 1 | 0 | 1 | 4 | 2 | 0 | 共享路径＋挥击帧候选 |
| HurtTargetGoal | TargetGoal | 0 | 0 | 1 | 0 | 0 | 0 | 事件驱动威胁传播 |
| LocalTargettingGoal | Goal | 0 | 0 | 1 | 0 | 0 | 0 | 事件驱动Linked传播 |
| FollowOthersGoal | Goal | 1 | 0 | 1 | 0 | 0 | 0 | 稳定伙伴表＋共享路径 |
| CalamityPathNavigation | GroundPathNavigation | 1 | 0 | 0 | 6 | 0 | 0 | 真实失败状态＋共享路径 |
| HybridPathNavigation | GroundPathNavigation | 1 | 0 | 0 | 6 | 0 | 0 | 真实失败状态＋共享路径 |
| Gorgon | EvolvedInfected | 3 | 3 | 0 | 4 | 9 | 0 | 通用查询/路径接管；技能保留 |
| Naiad | EvolvedInfected | 4 | 8 | 2 | 3 | 3 | 0 | 通用查询/路径接管；水陆技能保留 |
| Howitzer | Calamity | 4 | 10 | 3 | 1 | 3 | 3 | 公共服务；AI Fix弹道适配保留 |
| Hollenhund | Hyper | 2 | 3 | 2 | 4 | 3 | 0 | 公共服务；专属攻击保留 |
| Vanguard | UtilityEntity | 4 | 6 | 2 | 5 | 1 | 3 | 公共服务；专属技能保留 |
| Proto | Organoid | 2 | 3 | 6 | 0 | 3 | 3 | 通用查询＋sporesrp软适配 |
| HiveTumor | Organoid | 2 | 3 | 4 | 0 | 3 | 1 | 通用查询；生成/器官逻辑保留 |
| Vigil | Organoid | 4 | 4 | 5 | 0 | 1 | 0 | 通用查询；支援逻辑保留 |
| Delusionare | Organoid | 2 | 1 | 6 | 0 | 2 | 0 | 通用查询；幻觉技能保留 |
| GastGeber | EvolvedInfected | 3 | 5 | 1 | 2 | 0 | 0 | 公共服务＋既有工作名额 |
| InfectionTendril | UtilityEntity | 3 | 1 | 1 | 2 | 0 | 1 | 通用查询＋既有卷须调度 |
| HohlMultipart | LivingEntity | 1 | 0 | 1 | 0 | 0 | 0 | 父实体缓存；位置逐Tick |

## 全部检测类清单

- 公共AI：AerialRangedGoal、AOEMeleeAttackGoal、BraionmilSwellGoal、BuffAlliesGoal、BufferAI、BusserFlyAndDrop、BusserSwellGoal、CalamityPathNavigation、ClimberMovement、CustomMeleeAttackGoal、ExpAirPathNavigation、FloatDiveGoal、FollowOthersGoal、GazenWaterLeapGoal、GrieferSwellGoal、HybridPathNavigation、LeapGoal、PhayerGrabAndDropTargets、PullGoal、ScatterShotRangedGoal、SearchAreaGoal、TransportInfected、VolatileSwellGoal。
- 基类：Calamity、Experiment、FallenMultipartEntity、HohlMultipart、Hyper、Infected、LeviathanMultipart、Organoid。
- 基础感染体：Bairn、InfectedDrowned、InfectedHazmat、InfectedPlayer、InfectedVillager、InfectedWitch，以及继承Infected公共逻辑的Human/Husk/Pillager/Trader等未覆写Tick类。
- Calamity：Gazenbrecher、Grakensenker、Hinderburg、Hohlfresser、Howitzer、Leviathan、Sieger、Stahlmorder、Verfalldrachen。
- 进化感染体：Bloater、Braionmil、Brute、Busser、Chemist、Conductor、Gargoyl、Gorgon、Griefer、Howler、Inebriator、InfectedEvoker、InfectedVendicator、Jagdhund、Knight、Leaper、Mephetic、Naiad、Protector、Scamper、Scavenger、Slasher、Stalker、Thorn、Volatile。
- 实验体/Hyper：Biobloob、Lacerator、Saugling、Brot、Grober、Hevoker、Hollenhund、Hvindicator、Ogre、Wendigo。
- 移动与导航：CalamityMovementControl、DragonFlightMoveControl、ExperimentalGroundMovementController、InfectedArialMovementControl、InfectedWallMovementControl、SmoothLookControl、UndergroundMovementControl、UndergroundPathNavigation、WaterXlandMovement。
- 器官体：Brauerei、Delusionare、HiveTumor、Mound、Proto、Tentacle、Umarmer、Usurper、Verwa、Vigil、Womb。
- Utility：ArenaEntity、CorpseEntity、GastGeber、Illusion、InfectionTendril、InfestedConstruct、NukeEntity、Reaper、ScentEntity、Specter、TumoroidNuke、Vanguard、WaveEntity。
- 投射物：AbstractGunProjectile、AcidBall、AdaptableProjectile、BileProjectile、DrownedFleshBomb、FleshBomb、HarpoonProjectile、StingerProjectile、TarBall、ThrownBlockProjectile、ThrownBoomerang、ThrownItemProjectile、ThrownKnife、ThrownSickle、ThrownSpear、Vomit、VomitHohlBall、VomitUsurperBall及继承公共投射物Tick但不覆写的方法类。

所有LivingEntity范围查询只在服务端Spore实体Tick上下文中进入共享索引；投射物继续使用既有共享宽相候选；非LivingEntity查询、客户端调用和签名不匹配类保持原逻辑。
