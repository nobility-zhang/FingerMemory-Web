# 词库冒险包

本文件定义已实现的首版内容格式、安装边界、第一章运行和后续升级规则。H5-01 已落地
`.fmbook` 预览/安装、坏包拒绝、版本与内容哈希冲突、owner 隔离、幂等回执和
内容寻址媒体存储；H5-02 已落地固定安装 run、剧情选择、学习卡牌遭遇、胜利/救援
分支、伙伴对白、营火与检查点恢复。H5-03 已落地三章故事、遭遇三选一牌组奖励、
账号级角色解锁/营地切换和按内容 ID 去重的剧情回忆。完整续作依赖、章节迁移和
GM 发布仍按路线后续实施。内容包是可移植的游戏内容，个人存档与学习记录的备份规则见
[数据迁移](../operations/data-migration.md)。

当前服务端使用 Flyway V8 保存不可变安装、安装回执和资源元数据，V9 保存 owner
范围内的冒险 run、命令回执、战斗绑定和不可变事件，V10 保存牌组、奖励候选、
角色解锁和剧情回忆。资源文件放在
`CONTENT_PACKAGE_ROOT` 指向的 owner/内容哈希目录；生产 Compose 使用独立
`content-data` 卷，不能把媒体 base64 写入 `fm_user_data`。Web 账号页先预览再确认
安装，预览不写数据库或资源。

三章可编辑源与成品包位于 `content/warm-lantern/`。可编辑 JSON/TSV 使用普通 Git，
`warm-lantern-1.0.0.fmbook` 保留第一章历史版本，`warm-lantern-2.0.0.fmbook`
提供完整三章并使用 Git LFS；`旅伴` 说话者在运行时映射到当前带队伙伴，
`portraitCharacterId` 可覆盖剧情节点的指定说话者立绘。

## 内容包格式

首版 `.fmbook` 是数据与媒体 ZIP，不含可执行插件。以下文件为当前必需项：

```text
manifest.json       # schemaVersion/namespace/bookId/contentVersion/engineRange/entryNodeId/seriesId/requires
vocabulary.tsv      # 稳定 wordKey、term、meaning、acceptedAnswers、hints
bindings.json       # chapter/node → wordKey 或明确的词条分组
story.json          # chapters/nodes/choices/transitions/encounter descriptors
cards.json          # 稳定技能 ID、费用、白名单效果、角色技能与初始牌组
encounters.json     # 敌人、意图序列、奖励池、battleRulesVersion
characters.json     # 稳定角色 ID、立绘表情资源和对白引用
assets.json         # 路径、hash、mime、尺寸、来源/许可、fallback
assets/             # PNG/WebP/JPEG、已声明音频；首版无 3D 模型依赖
```

包内 TSV 固定有表头 `wordKey, term, meaning, acceptedAnswers, hints`（实际以 Tab 分隔）；acceptedAnswers/hints 是 JSON 字符串数组，由标准 TSV 引用/转义承载，空字段解释为 []，不能用逗号再次手工切分。普通旧 TSV 仍是无表头的词面/释义/提示列，导入器必须显式区分两种格式。编码 UTF-8，包内 wordKey 唯一且稳定。

固定节点类型为 dialogue、choice、explore、encounter、camp、chapter-end。首版遭遇类型为 `card-battle-v1`，溪桥守卫是该类型的配置实例。每个选项必须有节点内唯一的稳定 `choiceId`；encounter 节点必须声明 `encounterId`、`victoryNodeId` 和 `rescueNodeId`，三个引用都由安装校验解析。包可配置背景、对白、词组绑定、技能参数和转移，不能执行 JS/Lua、动态 eval、任意 HTML 或自由表达式。

条件使用有界白名单，如 flagEquals、nodeCompleted、encounterResolved、characterUnlocked、bondMilestoneReached；动作使用 setFlag、startEncounter、unlockNode、unlockCharacter、finishChapter、finishBook。保存前验证目标节点、资源/角色/词条引用、可达入口与结局、可退出路径和非法自动循环。需要循环重访的营地必须是显式玩家动作，不能无限自动执行节点。

`bookId + contentVersion + contentHash` 识别不可变内容；`wordKey/nodeId/characterId` 不使用数组位置或展示名称作身份。每次安装生成 owner 范围映射，其他账号不能通过包 ID 读取私有素材或存档。包内 wordKey 绑定至现有 studyItemId，复用已有学习目标需要明确映射，不自动按单词拼写合并。

## 包导入、升级与资源边界

- 当前实现使用 Commons Compress/CSV；限制 1000 文件、50 MiB 压缩包、200 MiB
  解压总量、20 MiB 单文件和异常压缩比；拒绝路径穿越、绝对路径、符号链接、加密包、
  重复或大小写冲突路径、可执行脚本及 SVG/HTML/GLB/GLTF。
- 初始可配置预算建议：压缩包 50 MiB、解压总量 200 MiB、1000 文件；结合真实样例验证，超限明确提示。解压时持续计量，不只相信 ZIP 声明。
- 首版拒绝 SVG/HTML 内容资源、GLB/GLTF、外链媒体与脚本。音频使用声明的 WAV/OGG/MP3 等经当前浏览器验证的格式，校验 MIME、字节上限和解码失败；来源/许可纳入资源清单。
- 内容哈希证明一致性，不证明作者可信；文字仍按纯文本渲染。模型和图片解析使用锁定的成熟库。
- 流程为临时解包→结构/语义/资源校验→导入预览→用户确认→原子安装。任何失败不留下半个词库或可见空章节。
- 本地资源放独立 Blob/内容资源存储；账号资源使用服务器管理的文件目录/卷与元数据，不把 base64 图片塞入现有 fm_user_data JSON。
- 文件先写 staging，内容寻址资源准备完整后事务切换 installed 清单；未安装文件不能被读取，失败回滚元数据并延迟回收孤立资源，不能宣称 MySQL 与文件系统天然同事务。
- 同账号相同包/版本/内容重复导入返回同一安装回执；同版本不同 hash 拒绝并要求作者递增版本。升级先预览词条、节点与存档影响。
- 进行中的 run 固定包版本。无迁移映射时可继续旧版或另开新 run；不得让正在进行的 nodeId 指向不存在的新内容。删除书包可归档并停止新 run，保留独立 FSRS 历史和可导出的故事记录。

## API 与备份扩展

扩展现有 OpenAPI，而非在前端发明接口。内容包预览/安装、run 创建/读取、剧情 action
提交、encounter 绑定/结算、三选一奖励、伙伴解锁和回忆回看已经实现；升级预览、
关系阈值和完整资源归档仍按后续路线实施。学习评估契约在 H2 定稿，内容/存档路径在 H5 定稿；
契约包含账号归属、策略/内容版本、幂等键、错误和恢复语义。

用户备份升级需覆盖 installation、run、encounter、关系/奖励回执、内容版本引用和资源清单；仅导出 metadata 但丢失私有素材不能称完整恢复。可导出含资源的版本化归档或明确列明的资源旁包，恢复先校验依赖与引用并进行原子可见性切换。旧 v3/v4 备份继续可按原路径迁移，新增故事默认为空，不凭旧复习日志生成历史剧情。详见 `docs/operations/data-migration.md` 新增边界。

## 场景服装变体

角色以带命名空间的 characterKey 标识（界面短 ID 只作别名），资源清单按 definitionVersion/costumeId/expression 引用，首版服装为 summer-travel 与 beach-swimwear。节点请求的服装/表情资源须在安装时验证；缺失明确 fallback 或拒绝安装，不能运行时任意抓取外链。服装只影响表现，不影响卡牌数值、FSRS 或关系值。存档保存节点和所需外观引用，跨端文字客户端可忽略图像而保留人物/剧情身份。


## 新故事 续章与续作

首版继续使用自包含的完整 `.fmbook` 包，不引入需要用户处理的无限依赖树或运行时补丁脚本。每次 contentVersion 发布均包含运行所需的词条、节点、角色定义引用和素材；对应用内置资源可引用明确版本，备份/导出须能解析回同一资源。

| 内容变化 | 身份与依赖 | 安装和存档行为 |
| --- | --- | --- |
| 新章节 | 同一 namespace/bookId，新 contentVersion，稳定旧 nodeId/wordKey | 新安装直接使用新版本；旧 run 在非战斗检查点预览映射后升级，已完成 run 可进入新增章节，旧完成回执不撤销 |
| 独立新故事 | 新 bookId，requires 为空 | 自己的 entryNode、角色、牌组和章节，不读取其他 run 的 flags |
| 同系列续作 | 新 bookId、相同 seriesId，requires 声明前作 bookId/endingId 和允许版本范围 | 安装允许，开始前显示依赖条件；只读取本用户正常模式的 CompletionReceipt |
| 新伙伴/外观 | characters.json 中的 namespaced characterKey 与新 definitionVersion/costumeId | 内容校验后加入定义注册表，是否可选由解锁事件和本书支持列表决定，不改应用中的角色枚举 |

名称与显示语言不是身份。`namespace` 是本地作者标识，不是可信签名；同名空间不能自动覆盖已安装人物定义。内置 `fm.core:lia/serin/fina` 的标准定义由应用/官方内容清单提供，其他包只能引用一致版本或声明自己的新角色，不能靠同 ID 替换脸、年龄或技能。私有包内容依旧有 owner 边界。

`requires` 首版只支持显式书籍/结局前置条件，不隐式下载依赖。缺前作、版本不支持或循环前置时拒绝开始并提供具体原因；不会阻止独立闪卡访问已经合法导入的词库。

完成一本书产生不可变 `CompletionReceipt`：owner、bookId/contentVersion、runId、endingId、允许继承的分支 facts、角色解锁来源、完成事件 ID。不同周目有不同回执；续作开始时选择其中一份符合条件的回执并固定为 sourceCompletionId，后续前作重玩不会改变正在进行的续作。

继承只有作者明确白名单的 branch facts、正常角色解锁和可用友情回忆，不复制前作敌人/牌序/整套牌组、凭据、笔记或隐私数据。技能牌组默认新书重新构筑；共享的英语学习目标仍依据显式 wordKey→studyItemId 映射复用。继承条件还需验证每种合法前作结局都有入口或明确不适用提示。

## 升级与删除的完整规则

- run 保存 content lockfile：包、engine/schema/battleRules、角色定义、素材版本与 hash。引擎支持范围不等于可以无声切换进行中战斗的规则。
- 新内容先完整安装校验，用户在营地/章节结束且没有待评分/待确认动作时选择升级；migrations 用声明式 ID 映射与受控默认值，禁止脚本。
- 词条纯展示修正、语义/标准答案变化、删除词条分别列出。语义变化默认新建学习目标；用户明确接受复用时才能映射旧目标，不能静默把“记住旧词”的状态套到新词。已记录日志继续指向原版本证据。
- 映射必须覆盖当前节点、flags、待选奖励、牌定义、伙伴与资源；不完整则保持旧 run 或新开 run。一次升级事务失败回到原版本，不出现一半新节点一半旧资源。
- 旧版仍有 run/回执/备份依赖时禁止清除相应内容与资源。归档只停止新开，不影响已开始的存档；永久清除先列出引用并要求处理依赖，不能级联清掉学习史。
- 同一节点/奖励换版本不能换 rewardKey 逃避去重；“修复旧奖励”与“新增奖励”要明确声明来源和迁移。恢复旧备份后的迟到请求另受 dataEpoch 保护。

## 角色定义 注册与加入

定义最小包含 characterKey、definitionVersion、name、简介/性格/目标、age、appearance、可用 costume/表情资产、对白事件表、默认称呼规则、技能定义引用、availability 与来源许可。当前角色美术遵循 [视觉规范](../product/experience.md)，不会因 GM 或内容包机制绕过适龄非性化要求。

角色可以是 NPC、可陪伴角色或可战斗伙伴，能力显式声明。每个角色的状态分为：定义已安装 → 剧情中遇见 → 加入可选名单 → 当前场景可用。出场可以早于加入；任何角色一经加入，也只在定义和资源仍可解析且本书允许时可选。

`unlockCharacter` 由指定节点的已确认事件触发，按 owner/characterKey 去重，不靠
前端按钮或包文件中的 `unlocked=true`。基础莉娅/瑟琳作为应用初始解锁；菲妮在
第二章相遇节点解锁，并可在后续营地切换为带队战斗伙伴。新的可招募人物不得要求
修改 `catalog.ts` 的硬编码 union 才能使用，已有短 ID 用显式 alias 迁移。

剧情回忆以 `namespace + bookId + recollectionId` 作为账号级去重键，不包含 run
或包版本；重复周目不会增加同一条回忆。关系阈值和 BondEvent 仍按后续路线实现。
用户切换陪伴角色不改变剧情指定说话者；节点显式声明的 `portraitCharacterId`
只覆盖当前场景立绘。

可陪伴/战斗伙伴应包含六表情、默认服装、关键事件对白和明确技能；普通 NPC 可仅 neutral 加显式 fallback。强制场景服装（如海滩）缺图要阻止该节点发布或提供作者定义且合适的完整 fallback，不能任意拿另一角色或不合场景的衣装替代。

同一角色跨书共享稳定 identity，不强制共享每一套战斗技能；本书允许的技能配置在开始遭遇时锁定。资源依赖缺失时显示“需恢复某内容包”而非删除角色回忆。换装只影响外观，不能刷新一次性技能。

## 作者制作与发布闭环

1. 从可编辑样例复制一部新书，设置 namespace/bookId/版本；填写词库、节点图、角色与奖励，不从应用代码复制一个新页面。
2. 编写章节入口、分支、胜利/救援、退出、结局与可回看片段；每个自动转移都有停止条件，每个必经遭遇都有失败后可继续的路径。
3. 通过现有 Web/Node 工具链增加内容校验和打包命令，读取同一权威 schema；打包前取回真实 LFS 资源，不能把 LFS 指针文本当作 PNG/音频。校验未知能力、坏引用、资产数量/大小、表情/服装、前置循环、不可达结局、奖励冲突和词库覆盖。
4. 在 GM 沙盒预览对白/人物，分别模拟主要分支、Again/无题、满牌组、失败救援、重试与升级；原始结果仅留本地忽略目录。
5. 发布完整不可变包。新普通用户安装，实际走完入口→解锁伙伴→战斗→章节结局；再用已有存档验证升级。作者源文件/成品/许可进入 Git，二进制用 LFS。
6. 一般新故事、支线、新人物、新外观只需要内容更新。新效果类型、节点类型或新语法须先升级应用并声明 capabilities/engineRange；未知能力包不能“安装成功但运行时才崩”。

GM 的共享内容目录与私有用户包分开。草稿只有管理预览可用，校验通过再发布；下架停止新安装/开局但保留已固定版本的可恢复存档。包 hash 保证一致性，不替代权限或作者授权。


## 扩展示例与兼容验收

第一部书使用稳定的 `warm-lantern` 身份；追加海滩内容发布完整新版本，作为第二章结束后的可选支线并保留直接去第三章的主线出口。已通关存档在安全检查点应用显式映射后可重访该支线，不撤销旧主线结局。独立新书如 `harbor-letters` 可不设前置；同系列续作如 `ancient-tree-oath` 则声明前作 main-ending，并导入指定白名单分支。

再制作一位有独立 characterKey 的新伙伴作为扩展验收样例：定义身份/动机/表情/服装/对白/技能→在节点相遇→触发加入→营地可选→遭遇锁定技能→结局留下回忆→新书引用同身份。若过程需要在应用源码补角色名字分支，H5-05 尚未完成。

词条只有一项、没有正式题、前作有不同结局、升级删除当前节点、海滩服装缺失、同 ID 但不同角色定义、归档仍被引用的版本，都必须形成明确结果或校验错误，不能靠作者“不这么做”保证可用性。
