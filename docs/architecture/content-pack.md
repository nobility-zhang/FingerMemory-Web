# 词库冒险包

本文件定义待实现的内容格式、安装/升级和媒体存储。内容包是可移植的游戏内容，个人存档与学习记录的备份规则见 [数据迁移](../operations/data-migration.md)。

## 内容包格式草案

首版 `.fmbook` 是数据与媒体 ZIP，不含可执行插件。目录示意：

```text
manifest.json       # schemaVersion/bookId/contentVersion/engineRange/entryNodeId
vocabulary.tsv      # 稳定 wordKey、term、meaning、acceptedAnswers、hints
bindings.json       # chapter/node → wordKey 或明确的词条分组
story.json          # chapters/nodes/choices/transitions/encounter descriptors
cards.json          # 稳定技能 ID、费用、白名单效果、角色技能与初始牌组
encounters.json     # 敌人、意图序列、奖励池、battleRulesVersion
characters.json     # 稳定角色 ID、立绘表情资源和对白引用
assets.json         # 路径、hash、mime、尺寸、来源/许可、fallback
assets/             # PNG/WebP/JPEG、已声明音频；首版无 3D 模型依赖
```

普通旧 TSV 不强制具备 wordKey，导入器显式分辨“普通词库”与包内格式。格式编码采用 UTF-8；列内数组采用统一、可校验编码，不用无说明的分隔符拼接。

固定节点类型为 dialogue、choice、explore、encounter、camp、chapter-end。首版遭遇类型为 `card-battle-v1`，溪桥守卫是该类型的配置实例。包可配置背景、对白、词组绑定、技能参数和转移，不能执行 JS/Lua、动态 eval、任意 HTML 或自由表达式。

条件使用有界白名单，如 flagEquals、nodeCompleted、encounterResolved；动作使用 setFlag、startEncounter、unlockNode、finishChapter。保存前验证目标节点、资源/角色/词条引用、可达入口与结局、可退出路径和非法自动循环。需要循环重访的营地必须是显式玩家动作，不能无限自动执行节点。

`bookId + contentVersion + contentHash` 识别不可变内容；`wordKey/nodeId/characterId` 不使用数组位置或展示名称作身份。每次安装生成 owner 范围映射，其他账号不能通过包 ID 读取私有素材或存档。包内 wordKey 绑定至现有 studyItemId，复用已有学习目标需要明确映射，不自动按单词拼写合并。

## 包导入、升级与资源边界

- 使用成熟 ZIP 库；限制文件数、压缩包总量、实际解压总量、单资源大小/像素和异常压缩比；拒绝路径穿越、绝对路径、符号链接、重复规范化路径、加密包和可执行脚本。
- 初始可配置预算建议：压缩包 50 MiB、解压总量 200 MiB、1000 文件；结合真实样例验证，超限明确提示。解压时持续计量，不只相信 ZIP 声明。
- 首版拒绝 SVG/HTML 内容资源、GLB/GLTF、外链媒体与脚本。音频使用声明的 WAV/OGG/MP3 等经当前浏览器验证的格式，校验 MIME、字节上限和解码失败；来源/许可纳入资源清单。
- 内容哈希证明一致性，不证明作者可信；文字仍按纯文本渲染。模型和图片解析使用锁定的成熟库。
- 流程为临时解包→结构/语义/资源校验→导入预览→用户确认→原子安装。任何失败不留下半个词库或可见空章节。
- 本地资源放独立 Blob/内容资源存储；账号资源使用服务器管理的文件目录/卷与元数据，不把 base64 图片塞入现有 fm_user_data JSON。
- 文件先写 staging，内容寻址资源准备完整后事务切换 installed 清单；未安装文件不能被读取，失败回滚元数据并延迟回收孤立资源，不能宣称 MySQL 与文件系统天然同事务。
- 同账号相同包/版本/内容重复导入返回同一安装回执；同版本不同 hash 拒绝并要求作者递增版本。升级先预览词条、节点与存档影响。
- 进行中的 run 固定包版本。无迁移映射时可继续旧版或另开新 run；不得让正在进行的 nodeId 指向不存在的新内容。删除书包可归档并停止新 run，保留独立 FSRS 历史和可导出的故事记录。

## API 与备份扩展

扩展现有 OpenAPI，而非在前端发明接口。规划资源包括内容包预览/安装/升级、run 创建/读取、action 提交、encounter 读取、assessment 类型与 reveal-for-check/评分，以及伙伴偏好/对话回看。学习评估契约在 H2 定稿，内容/存档路径在 H5 定稿；契约要包含账号归属、策略/内容版本、幂等键、错误和恢复语义。

用户备份升级需覆盖 installation、run、encounter、关系/奖励回执、内容版本引用和资源清单；仅导出 metadata 但丢失私有素材不能称完整恢复。可导出含资源的版本化归档或明确列明的资源旁包，恢复先校验依赖与引用并进行原子可见性切换。旧 v3/v4 备份继续可按原路径迁移，新增故事默认为空，不凭旧复习日志生成历史剧情。详见 `docs/operations/data-migration.md` 新增边界。

## 场景服装变体

角色固定 characterId，资源清单按 costumeId/expression 引用，首版服装为 summer-travel 与 beach-swimwear。节点请求的服装/表情资源须在安装时验证；缺失明确 fallback 或拒绝安装，不能运行时任意抓取外链。服装只影响表现，不影响卡牌数值、FSRS 或关系值。存档保存节点和所需外观引用，跨端文字客户端可忽略图像而保留人物/剧情身份。
