# 游戏运行时与存档

本文件定义 Phaser/Vue 边界、卡牌命令、音效、故事进度与文本降级。最小音频 Game 生命周期、首个翻面音、纯 TypeScript `card-battle-v1` 规则和账号模式 Java 战斗命令已落地；故事检查、可玩战斗界面和完整音效矩阵仍待实施。游戏数值和玩法规则以 [产品设计](../product/overview.md) 为准，不在这里维护第二份伤害/费用表。

## 责任划分与模块

按现有结构渐进增加 `web/src/adventure/`（内容图、纯状态机、检查点）、`web/src/scenes/`（Phaser 适配）、`web/src/companions/`（画像/对白/用户偏好）与 `web/src/content/`（包解析、校验和版本）。这只是建议目录，不要求先创建空框架。

```text
导入内容包 → 校验与版本安装 → 内容图/场景描述
                              ↓
用户选择/探索 → AdventureRuntime → Vue 对话/卡片 + Phaser SceneAdapter
                              ↓
评估会话 → 既有学习服务 → 固定 FSRS worker
                              ↓
确认的学习事件 → 同事务故事推进/奖励回执 → 渲染与角色反馈
```

学习核心不依赖 Phaser、角色或特定故事。内容图只能调用已注册的动作；渲染器只接收状态与产生选择意图。画面播放结束不构成事务提交，动画重播不产生新奖励。

## 存档与数据模型

| 实体 | 关键身份与用途 |
| --- | --- |
| ContentPackage / AssetManifest | 不可变包版本、内容 hash、资源白名单、安装状态 |
| UserBookInstallation | owner、包版本、词条映射、导入回执与归档状态 |
| AdventureRun | owner、installation、节点/章节、分支 flags、runVersion、已提交检查点 |
| EncounterState | run/node/encounterId、规则版本、回合、队伍/路障状态、待处理学习目标与行动 |
| CompanionPreference / BondEvent | owner、带队角色、称呼/密度、去重的关系事件 |
| AdventureEventReceipt | owner、eventId、来源学习 submission/attempt、请求指纹和原始结果 |
| LearningContributionReceipt | owner/dataEpoch/learningEventId 唯一，锁定一个回合或营地委托 |
| CharacterDefinition / UserCharacterUnlock | 内容版本中的稳定角色定义，与 owner 范围解锁来源分别存储 |
| CompletionReceipt | owner、书/版本、结局、run、继承 facts 白名单与完成事件，不可变 |
| RewardOffer | owner/run/节点/奖励槽、首次固定候选、领取/跳过结果与版本 |

这些实体与复习状态分离。可复用当前单数据库事务及用户锁策略，但媒体文件不进入大快照；需要新表时用新增 Flyway，不改已运行的迁移。个人内容规模下先做正确性，不为未来开放世界拆微服务。

帐号正式回合流程：Java 验证用户、内容安装、当前遭遇/队列绑定与版本→原事务边界外调用 worker→事务内复核版本/回执→一起写学习状态、日志、故事事件、能量和奖励回执。空/重复事件、旧事件重放和迁移导入不能推进新遭遇。

引导练习可作为遇敌教学，但章节规则必须声明何种练习事件有资格贡献一次行动。学习任务在作答前锁定唯一 contributionTarget（回合或营地委托），按 `(owner, dataEpoch, learningEventId)` 建立唯一贡献回执，而非仅在 encounter 内去重，同时防止同一动作 ID 改请求体；run 内技能牌按 owner/run/rewardSlot 去重，账号级纪念品/角色/关系事件按 owner/rewardNamespace/rewardKey 去重（不含 run 或包版本）。奖励与自评高低无关。服务端不接受客户端自报“杀敌/通关/好感 +100”。

分支选择使用独立幂等 actionId 与 runVersion；请求竞争返回冲突并提供可恢复状态。渲染失败不会撤销成功学习；恢复直接读最新检查点，可以补播或跳过反馈动画，不重跑领域事件。角色表情属于表现状态，私有偏好/关系值属于持久化状态，不能混放。

## 队列与文本降级

FSRS 到期队列优先，章节配置只约束新词来源。回合开始时可用正式题不足，保持 pending-learning 状态，允许暂停到营地或由用户明确改为仅练习；不得为补题修改敌方生命、判胜负或突破学习上限，不为通关突破日上限。每个任务锁定 wordKey→studyItemId 与内容版本；剧情曝光过的目标不能被同一回合伪装成独立回忆。

每个 explore/encounter 节点提供可读说明、标号选项与动作结果；CLI 和 WebGL 不可用的 Web 使用同一事件/校验协议。CLI `adventure list/start/resume` 消费已提交检查点；跨端不传 DOM、WebGL 状态或动画帧。终端不可复现的纯装饰可跳过，核心分支与学习结果保持一致。

## 游戏引擎选型与 Vue 接入

选用主流开源 H5 框架 Phaser，沿用现有 Vue 3 + TypeScript + Vite。二维卡牌、回合制遭遇、场景切换和音效直接使用框架能力；不同时安装 Phaser 与 Three.js。当前依赖清单尚无二者，因此无需迁移一个已经完成的 3D 游戏引擎。

实施时核对 Phaser 的稳定主版本、官方 Vue/TypeScript 模板、当前 Vite/TS 兼容性与许可，锁定精确版本。官方模板与在线 API 文档可能处于不同主版本，禁止混用；只借鉴接入方式，不覆盖已有工程或把模板演示当产品。若选定版本不可用，说明兼容问题并在同一框架稳定分支内调整。

Phaser 负责 Scene、资源载入、精灵/卡牌表现、Tween、粒子和 SoundManager。Vue 保留认证、书架、对白、原生输入、闪卡评分和可访问的手牌/目标控件。建立单一 Game 生命周期和显式事件桥；应用状态由领域服务产生，不能由 Vue 和 Phaser 各写一套。可用原生 DOM 键盘路径完成所有操作，手牌拖拽只是可选增强。

当前最小实现使用 Phaser `4.2.1`（MIT，`package-lock.json` 锁定）的 Canvas renderer，在闪卡会话内创建一个只负责音频与星灯反馈的 Game。Vue 通过类型化事件桥发送 `audio-unlock`、`audio-mute`、`audio-play`，Scene 回传 `game-ready`、`audio-unlocked`、`sound-played`/`sound-error`。Game 在宿主组件挂载时创建、卸载时只销毁一次；当前页面不创建第二个 Game。

浏览器懒加载游戏引擎；经典闪卡独立打开时不必初始化整个 Game/音频系统。Scene 退出时移除监听、计时器、Tween 与不再需要的音效，真正退出游戏时销毁 Game；缓存资源有明确上限与所有者。后台暂停和减少动画不影响既有已提交状态。

参考：[Phaser 官方介绍](https://phaser.io/oss/docs/phaser/getting-started/what-is-phaser)、[官方 Vue TypeScript 模板](https://github.com/phaserjs/template-vue-ts)。框架自身支持不等于本项目的全部浏览器已通过验收。

## 音效运行时与性能

只用 Phaser SoundManager 管理游戏音效，禁止再引入另一套大音频框架。经典卡片可通过同一轻量适配或浏览器原生音频使用同一资源清单，不为了翻面音初始化全套战斗场景。

- 首次显式交互后解锁音频，正常处理 autoplay 拒绝；解锁失败不阻止作答与提交。
- `audioEventId` 与已确认的 action/review 回执绑定，重试不重复播放命中与结算。回看剧情可播放相应叙事声音，但不能重新领取奖励。
- 全局/音效音量、静音和场景所有权明确；SoundManager 为 Game 级，切 Scene 不会自动终止所有声音，需清理循环音和订阅。
- 初始同时发声上限建议 8，悬停/抽牌等短事件限频；音量不随重复播放不断叠加。切号、暂停、后台和退出时按策略停止或淡出。
- 文件缺失、解码错误、无音频输出环境提供可定位诊断和无声降级；开发测试必须实际试听，不只验证开关。
- 初始预算目标：第一可交互场景资源不超过 8 MiB，按章按需载入；目标笔记本 1080p 动画至少 30 FPS，输入响应 p95 不高于 100 ms。以真实设备验证并记录本地报告，不伪造达标。
- 进入/退出场景 10 次后，监听、音频实例、纹理和计时器不随次数无界累积。WebGL 失效时使用框架已验证的 Canvas 能力或 DOM 静态战斗，不阻断同一领域命令。

参考：[Phaser 音频与自动播放](https://docs.phaser.io/phaser/concepts/audio)。

## 卡牌战斗状态与学习桥接

当前纯规则边界位于 `web/src/battle/`。`BattleState` 固定保存 `battleRulesVersion='card-battle-v1'`、schema/state 版本、种子后的 RNG、抽牌游标、手牌/弃牌/耗竭区、敌我生命与护盾、敌人意图和终结状态。`playCard`/`endTurn` 都校验 expected state version，返回不可变新状态和类型化表现事件。

账号模式已由 Java 的 `battle` 包接入同一规则版本：Flyway V6 保存战斗快照、每回合唯一学习任务、不可变表现事件和命令回执；创建、学习授能、出牌、伙伴技能和结束回合均使用 owner 范围、`Idempotency-Key` 与 expected state version。正式复习回执必须在同一事务中完成唯一授能，状态、事件、能量奖励和回执一起提交；当前尚未接入 H4 的玩家战斗界面、内容包遭遇和故事检查点。

`web/src/battle/fixtures/card-battle-v1.turn.json` 是共享确定性 fixture：从固定教学牌序执行守护、突刺、余烬、观察和结束回合，最终进入第二回合。规则测试还覆盖种子洗牌、版本冲突、目标/费用、护盾优先、灼烧刷新与致死、弃牌回收、耗竭不回洗、空堆少抽、胜负优先级和版本化 JSON 恢复。内部浏览器入口为 `/rules/card-battle-v1`，只运行真实规则，不提供玩家战斗假流程。

战斗状态至少包含 `battleRulesVersion`、encounterId、turnId、phase、stateVersion、敌我生命/护盾/状态、敌方当前意图、牌实例/抽牌堆/弃牌堆/耗竭区/手牌、能量、一次性伙伴技能、洗牌状态与当前学习目标绑定。静态 cardDefinitionId 与本场 cardInstanceId 分离。

状态机按“回合准备→学习→结果确认→出牌→敌方行动→下一回合/结算”运行。伤害、费用、护盾、灼烧、治疗、抽牌和耗竭的明确顺序见 [产品设计](../product/overview.md)；没有定义的机制不由动画代码自行补出。

- 在线账号由 Java 保存并验证战斗状态，决定洗牌序列及抽牌游标；不接受客户端自报手牌、能量、伤害和胜利。
- `play-card` 携带 actionId、预期版本、cardInstanceId 与目标；检查归属、阶段、手牌存在、费用和目标合法性，再原子更新状态并返回只读结果/表现事件。
- 每回合绑定唯一学习任务。学到结果后，按既定 formal 或 practice 路径产生唯一事件，最多授予一次本回合能量；高低自评分不改变基础能量。
- `end-turn`、伙伴技能、奖励选择均有幂等回执；同 ID 不同内容拒绝。失联后先确认提交结果，不使用新 ID 盲目补打一张牌。
- 状态更新、相关事件、奖励和检查点处于同一事务；学习事件需要调度 worker 时继续使用现有事务外计算、事务内版本复核模式。
- 本地模式采用同一版本的规则配置与一组共享确定性场景 fixture 验证一致性；不维护与账号模式含义不同的随机战斗。此工作不要求复制整套 Java 安全/存储层到浏览器。
- 固定教学牌序、普通洗牌结果和 RNG 状态保存后不可通过刷新重抽。算法库不是密码学身份工具；认证令牌继续使用安全随机来源。
- CLI 消费相同卡牌/目标/结束回合命令与检查点，用编号手牌和文本意图替代图形，不能重新推算另一份云端伤害。

## 现有模式边界与退场

现有 M5 代码采用随项目构建的可信 TypeScript 注册表，不具备安全沙箱。以下是迁移基线；H6 移除歌词/弹幕/卡片雨的新建入口及无调用实现，历史读取兼容保留，不要求维持四模式产品。现有契约：

- 经典闪卡使用独立的 `flashcard` 会话模式，不依赖歌词/弹幕/卡片雨的呈现插件。正面只显示问题；Space 翻面后进入 `reveal-for-check`，数字 1–4 对应四档自评；直接“没想起”只开放 Again。展示层只调用共享 repository/API 的正式复习命令，不能自行推进 FSRS。

- `id`、显示名、版本、能力声明和默认配置。
- `setup(context)` 返回组件和 `start/stop/dispose`；上下文提供会话/词条只读查询、会话事件订阅，以及取目标、练习和正式复习命令。
- 注册表拒绝重复 ID、空身份、重复能力声明和越界配置；同一时刻只激活一个模式插件。
- 返回 `dispose()`，必须清理键盘监听、定时器、`requestAnimationFrame`、观察器、音频和 DOM 订阅。
- 模式组件只负责呈现和选择目标；输入、提示、揭晓、跳过、评分提交、会话保存和资源清理由共享工作台统一处理。
- `appMeta['practice.modePreferences']` 保存禁用的模式 ID，并保证至少一个模式可用；禁用后保留全部学习数据。
- `SessionWorkbench` 在卸载时停止并销毁当前插件，移除 `visibilitychange` 监听并尽力暂停保存；歌词定位使用 CSS transition，不创建 interval 或 `requestAnimationFrame`。
- 弹幕模式使用 CSS 横向漂移和点击选目标；减少动画时改为静态标签布局，不监听动画事件，也不把命中、离开屏幕或超时映射为评分。
- 卡片雨模式使用 CSS 纵向下落和点击选目标；减少动画时改为静态卡片网格，等待、落地或消失均不产生作答，只有共享评分命令能推进 FSRS。

远程安装、动态 `eval`、第三方脚本和跨站 iframe 不属于第一版。


## 版本 会话与跨端恢复

学习会话、AdventureRun 与 Encounter 是不同寿命的对象：一部书可以有多个学习会话，不能把“同一目标每会话一次评分”扩大成整本书永远只能复习一次。每次学习小组有有限队列和明确 sessionId；再次到期时可开启下一学习会话。不要为了重复授能无条件为每张相同卡新建会话。

开始 run 时固定内容 lockfile、人物/服装/技能定义版本、battleRulesVersion；每个遭遇固定伙伴与牌组，进入回合时锁定一个学习任务。另一端先复习同目标后，本端提交先按幂等识别成功回执，否则返回版本冲突，保留输入并让用户刷新/重新领取任务；不能把已失效题目的自评自动应用到新卡片或直接授能。

暂停/退出保持当前阶段；明确救援/撤退才结束战斗。恢复保存完整手牌/牌序/游标/能量/状态效果/待选奖励/回执，重放展示不执行领域命令。复习成功但动画失败仍算成功，不能补写第二份日志。无合法牌可打时可以结束回合；满手/空堆/候选奖励不足按产品规则收束。

新增角色与剧情由已安装包的运行时注册表解析，不能把角色名、章节或卡牌 ID 写成遍布前后端的 switch。注册表只接受校验通过的有版本定义；新增效果类型或未支持 schema 仍需要应用发布，包不能自带可执行代码绕过能力边界。

## 环境与回执隔离

所有正式数据及回执绑定 owner 和单调变化的 dataEpoch。正常账号恢复/整体重置切换 epoch 并使旧会话、请求和游标失效；幂等先在相同 epoch 内识别，旧 epoch 请求不能在恢复后重新执行。导出的旧回执用于历史诊断，不作为新 epoch 的可执行动作许可。

GM sandbox 是单独的数据空间，绑定 admin、测试用户/夹具、独立时钟、随机源和 epoch；同一个业务应用服务使用明确 ExecutionScope 选择数据，不允许由普通客户端字段选择 GM scope。H3-04 的 `card-battle-v1-tutorial` 使用独立
`fm_gm_sandbox` 表，跳节点、发牌、解锁、虚拟时钟和重置均不写入正常账号的 FSRS、
牌组、角色名单、续作前置回执或统计。具体权限与命令见 [GM 设计](../operations/gm.md)。
