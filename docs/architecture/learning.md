# 学习模型与复习一致性

本文件定义学习对象、存储、认证与复习提交边界。既有实现和待实施的学习规则 v2 分开描述；玩法和视觉不能改变这些不变量。系统入口见 [架构概览](overview.md)，具体算法选择见 [FSRS 适配](fsrs.md)。

## 数据模型

### M1 核心表

M1-02 落地 schema v1；当前已通过 Dexie v5 迁移到词条、学习目标、练习会话、练习、复习状态和日志分离的结构，并补齐会话队列来源与学习规则 v2 评估元数据。定义位于 `web/src/storage/database.ts`，页面只接收 repository 接口，不直接访问 Dexie 表。

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `appMeta` | `key`, `value` | 应用数据版本、最后备份时间等元数据。 |
| `lexicons` | `id`, `name`, `description`, `createdAt`, `updatedAt`, `archivedAt` | 用户词库；归档不同于删除。 |
| `words` | `id`, `term`, `meaning`, `acceptedAnswers`, `hints`, `normalizedTerm`, `createdAt`, `updatedAt` | 静态词条；不直接保存正式掌握度。 |
| `studyItems` | `id`, `wordEntryId`, `direction`, `createdAt`, `updatedAt` | 学习目标；首版方向固定为 `meaning-to-spelling`。 |
| `lexiconItems` | `[lexiconId+studyItemId]`, `lexiconId`, `studyItemId`, `order`, `sourceHints` | 词库成员和顺序；同一学习目标可出现在多个词库。 |
| `notes` | `id`, `studyItemId`, `content`, `createdAt`, `updatedAt` | 个人笔记，引用学习目标而不复制词义或复习状态。 |
| `practiceAttempts` | `id`, `studyItemId`, `modeId`, `direction`, 作答与揭示标记 | 练习事实；写入时不改变正式复习状态。 |
| `practiceSessions` | `id`, `modeId`, `status`, 队列快照, 当前目标, 草稿, 待评分答案, 速度/动画设置, `endedReason` | 支持暂停、后台切换、退出、错词重练和再次进入；不保存掌握度。 |
| `reviewStates` | `studyItemId`, FSRS 卡片字段, `dueAt`, 调度版本与配置快照 | 每个学习目标唯一一份正式调度状态。 |
| `reviewLogs` | `submissionId`, 评分、作答证据、前后卡片和 ts-fsrs revlog | 不可变正式复习记录；主键天然保证提交幂等。 |

### M3 队列策略

- `appMeta['practice.queueSettings']` 保存新词上限、复习上限和队列暂停标记；默认最多 10 个新词、50 个到期复习。
- 本地模式开始会话前按浏览器 IANA 时区计算学习日；账号模式按账号已保存的 IANA 时区计算，不能用请求参数切时区重复领取额度。迁移现有接口时需明确兼容边界。仓储将到期复习按 `dueAt` 优先排列，再补入从未产生正式复习状态的新词。
- 计划结果是有上限且去重的快照；暂停时可用数量和到期数量仍显示，但选中队列为空，后台不会继续取题。
- 会话队列项保存 `new`、`review` 或 `retry` 来源。重练只把当前目标追加到同一会话，不重新规划队列。

时间点统一保存为 UTC epoch 毫秒，展示和“学习日”按用户当前时区转换。排序同时使用显式顺序和稳定 ID，不依赖对象插入顺序。

### 服务端 P1 表

Flyway `V1__identity_and_lexicons.sql` 建立：

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `fm_user` | `id`, `username_normalized`, `password_hash`, `status`, `version`, 时间毫秒 | 用户身份；用户名唯一，密码只存 BCrypt 哈希。 |
| `fm_user_preferences` | `user_id` PK, `timezone`, `daily_goal`, `theme`, `shortcuts_json`, `version` | 用户偏好；应用层校验用户存在，不使用物理外键。 |
| `fm_lexicon` | `id`, `owner_id`, `name`, `normalized_name`, `source`, `archived_at_ms`, `version` | 私人词库；唯一约束包含 owner。 |
| `SPRING_SESSION` | Spring Session 标准字段 | HttpOnly Web 会话，Flyway 管理 schema。 |
| `SPRING_SESSION_ATTRIBUTES` | `SESSION_PRIMARY_ID`, `ATTRIBUTE_NAME`, `ATTRIBUTE_BYTES` | 会话属性；应用清理，不增加物理外键。 |

### 服务端 P2 数据边界

Flyway `V2__online_learning_and_receipts.sql` 增加：

| 表 | 说明 |
| --- | --- |
| `fm_user_data` | 每个用户一行 schema/data format v5 快照，保存词条、学习目标、笔记、练习、会话、正式状态和日志；旧 v4 行在 V5 迁移后保留原业务记录，事务内 `SELECT ... FOR UPDATE`，`data_version` 单调递增。 |
| `fm_review_receipt` | 用户范围正式复习 submissionId 唯一回执，保存请求指纹和第一次响应。 |
| `fm_reward_event` | 用户、事件类型和事件 key 唯一；正式复习奖励与状态/日志同事务，导入历史标记为不可奖励。 |
| `fm_migration_receipt` | 用户范围迁移批次唯一回执。 |
| `fm_import_id_map` | 迁移批次内原 ID 到目标 ID 的稳定映射。 |
| `fm_backup_receipt` | 用户范围恢复幂等回执。 |

Flyway V14 为 `fm_user_data`、战斗/冒险 run 和关键命令回执增加 `data_epoch`。
正常保存保留 epoch；备份恢复先写入恢复内容，再为当前 owner 生成新 epoch，并把
恢复的 battle stateVersion/runVersion 提高一个固定偏移。旧命令因 epoch/版本不匹配
不能复活，历史回执仍可按恢复前 epoch 保留诊断。

`V3__backfill_user_data.sql` 为 P1 升级前已有账号回填空 v4 快照，避免升级后首次并发访问竞争创建同一用户行。

选择用户行锁加紧凑 v5 快照，而不是为个人规模拆成大量热表：它让会话、日志链、状态和迁移映射共享一个清晰事务边界，也直接复用客户端既有 v4/v5 校验模型。`fm_lexicon` 仍是独立词库投影；导入批次通过 `import_batch_id` 隔离同名和 ID 空间。后续规模证据需要拆表时，必须保持现有 API、回执和版本语义。

## API 与认证边界

- 唯一 API 契约是 `docs/api/openapi.yaml`；当前 P2 实现 auth、lexicons、words、queue、sessions、formal reviews、migration 和 account backup，其余路径明确标记 planned。
- Web 同源使用 HttpOnly `FM_SESSION`、SameSite=Lax、CSRF header；不得把会话令牌存 localStorage。
- CLI 后续使用 Bearer 个人访问令牌；服务端只保存摘要，CSRF 只对 Bearer 请求豁免，Cookie 请求不被全局关闭保护。
- 所有资源归属来自认证 principal；路径中的 userId 不被接受。跨用户读取返回 404，避免泄漏对象存在性。
- 登录/注册由 Bucket4j 按 IP 和用户名限流；日志不得输出密码、token、Cookie 或完整凭据对象。
- BCrypt 密码按 UTF-8 字节限制为最多 72 字节，避免算法静默截断；停用账号的既有会话在下一次 API 请求时被拒绝并失效。
- 会话由 Spring Session JDBC 持久化；学习会话保存在用户 v4 快照中，以 `dataVersion` 和会话 `version` 双重 CAS；新建登录不会废弃其他设备会话。

## 正式复习服务边界

P0 核查结论详见 `docs/architecture/fsrs.md`：当前没有通过 FSRS-7/34 权重对照的 JVM 调度器。

- Java 不直接计算正式卡片，不信任客户端上传 afterCard。
- P2 通过内部固定版本 TS worker 计算；Java 做认证、归属、门禁、幂等、版本检查、服务端调度时间和事务。
- submissionId 重复检查优先于状态版本冲突；同 ID 同内容返回首次结果，不同内容冲突。
- 状态、正式日志和奖励事件在一个数据库事务中提交；worker 或事务失败时不留下半份结果。
- 预览和提交使用同一卡片、配置和 reviewedAt；四档预览不写库。
- worker 调用发生在数据库事务外；成功后 Java 再锁用户行，重新检查回执、dataVersion、sessionVersion、reviewVersion 和 beforeCard，条件全部一致才原子写入。
- 同 submissionId 的快速和事务内回执检查都先于旧版本冲突；新 submissionId 基于旧复习版本返回 `REVIEW_VERSION_CONFLICT`。

### 数据一致性

- 词库成员不得引用不存在的学习目标；删除学习目标前必须处理成员关系、笔记和会话引用。
- 删除或归档词库只影响后续新会话的取词，不删除学习目标、练习、复习状态和日志；未完成会话按队列快照中的学习目标 ID 独立恢复，不依赖词库当前成员关系。“移除词条”表示移除当前词库成员，不硬删除共享学习历史。未完成会话引用的学习目标不能直接移除。
- 正式复习使用 IndexedDB 事务同时写入 `reviewStates` 和 `reviewLogs`。
- `reviewLogs.submissionId` 建立唯一索引；重复提交在事务内检测并返回已有结果，不再次调用调度推进。
- `reviewStates` 保存 `schedulerPackageVersion` 和 `fsrsModelVersion`。升级库或模型不能静默重排已有卡片。
- 同一提交 ID 但请求内容不同必须报冲突，不能返回一个看似成功但语义不同的结果。
- 正式复习时间不得早于该学习目标已有的 `lastReviewAt`；练习记录与正式复习日志分别保存。
- 会话队列必须引用存在的学习目标；当前索引必须对应队列中唯一 `currentStudyItemId`；待评分答案必须属于当前会话。
- 旧本地会话的错词重练仅追加当前目标，不新建复习状态；其开始新轮时废弃旧会话的策略不能扩大到不同设备或故事 run。新冒险仅显式结束自己的会话，保留其他可恢复检查点。
- 现有 legacy-v1 抄写门禁以 `answerRevealed` 拒绝正式复习；H 阶段按下文学习规则 v2 区分检查答案与事先曝光。IME 组合期间的 Enter 不提交。
- 备份和恢复使用完整数据库边界；恢复开始前保存当前数据，校验、资源重建或写入失败
  时整体回滚。完整账号备份另见 [数据迁移](../operations/data-migration.md)。
- 备份是版本化 JSON `StorageSnapshot`，包含全部业务表和 `appMeta`。选择该格式而不是二进制数据库转储，是为了在覆盖前执行 Zod 结构校验、跨表引用校验和未来版本迁移；不手写 IndexedDB 底层序列化。
- TSV 导入以 `appMeta` 中的批次回执保存幂等结果；同一批次与同一输入重复提交返回第一次结果，同批次不同输入报冲突。
- 每个学习目标最多保存一条笔记；保存前确认学习目标存在，空内容清除笔记。笔记与词条、会话和调度状态分离。

## 复习调度

- `scheduler/` 是唯一可以调用 ts-fsrs 的边界；模式接收命令结果，不直接计算稳定度、难度或到期日。
- 当前锁定 `ts-fsrs@6.0.0` 的异步 `DefaultScheduler()`、FSRS-7、`dateChrono`、`scheduler.newCard()` 和 `scheduler.review({ card, grade, now })` API；未使用已废弃的 v5 类与字段。
- FSRS 卡片的时间点转换为 UTC 毫秒快照，调用库时再转换为 `Date`；状态保存 `stability`、`stabilityFast`、`difficulty`、`dueAt`、`lastReviewAt`、`state`、`scheduleStatus`、`reps`、`lapses`、`scheduledDays` 和 `learningStep`。
- 调度配置快照保存模型/包版本、权重、期望留存率、学习/重学步骤、最大间隔、是否启用 fuzz 等字段；版本或配置不一致时拒绝继续推进已有状态。
- M3 通过测试夹具固定输入时间、评分和模型参数，验证相同输入得到稳定结果；只断言能反映失败路径的字段，不复制整个第三方输出对象。
- 评分使用 Again/Hard/Good/Easy 显式映射；输入正确性、输入速度、动画结果和提示状态只形成作答事实或正式复习门禁，不直接代替用户评分。
- 评分前通过只读 `previewReviewIntervals` 使用当前卡片、锁定配置和同一个评分时间计算四档预计到期；预览不写状态或日志，最终提交仍由 `submitReview` 原子推进。
- legacy-v1 日志继续按旧的隐藏/无提示门禁只读解释；新本地与账号流程使用
  `assessmentPolicyVersion=2` 的显式评估元数据，不能只改 UI 或把旧日志回算成新规则。
- 旧 A1 是历史策略，不再作为翻面卡片的限制；A3 同一目标同一会话最多一次正式复习的约束继续有效。旧日志保持原始语义，不回算。
- 同会话上限由 `submitReview` 在状态与日志事务内再次检查，不依赖 UI 按钮状态；重复的 `submissionId` 仍先按幂等返回原结果。
- 诊断查询以只读事务汇总每个学习目标唯一的状态、词条和全部不可变日志，并核对相邻日志前后卡片及最新日志与当前状态；异常返回可定位的问题文本，不修改数据。
- 会话工作台在模式组件之外读取并展示队列目标的唯一正式状态；呈现层不能缓存或另建掌握度。歌词/弹幕/卡片雨只保留旧会话兼容，不能新建。

## 学习规则 v2 与版本兼容

- `assessmentType` 为 `typed-recall`、`flashcard-self-report`、`guided-practice`；和模式、遭遇、客户端来源分别保存。
- 当前本地 v5、服务端 DTO/快照与 OpenAPI 同名字段为 `assessmentType`、
  `assessmentPolicyVersion`、`assessmentStage`、`priorExposure`、
  `explicitNotRemembered`；正式日志的阶段为 `rate`。
- 翻面卡片是 prompt→attempt→reveal-for-check→rate；reveal-for-check 合法后允许四档评分。主动“没想起”进入检查答案并明确 Again。服务端不能将揭晓统一当作弊，也不能只凭客户端一个布尔值跳过阶段检查。
- 拼写回忆独立作答后才揭晓；作答前请求帮助可选择记录失败 Again，再进入 guided-practice。不得先抄正确答案再算 Good/Easy。
- 保持现有同目标同会话一次正式提交；Again 的下一学习步骤按调度新建到期任务/后续会话，而非同会话无限重记。
- 旧记录缺失策略字段时解释为 legacy-v1，不补写字段、不重算、不追溯补发奖励。当前本地 schema/data format、备份导入和服务器快照使用 v5；迁移入口继续接受 v4 并为新记录写入 v5，未来格式必须拒绝而不是降级误读。
- 不重放旧日志、不改变 FSRS 模型/权重、不追溯补发剧情或经验。扩展 OpenAPI、Zod、Java 校验器、worker 输入边界测试和 CLI 类型时同步提供新旧 fixture。


## 任务领取 贡献与跨端

正式任务领取在服务端（本地模式为本地事务）核验 owner、学习日、目标版本、日限额与同会话限制，产生有作用域的 assessmentTaskId。题面/答案版本在领取时冻结；打开卡片或翻面本身不算完成，也不自动领取额外奖励。

账号 daily limit 按已提交和仍有效领取的份额计算，普通改时区在下一学习日生效，不能反复改时区刷配额。取消/过期未完成任务按规则释放份额，重试同任务不重复占额。同目标并发领取可以拒绝/提示已有任务，或在提交时 CAS 拒绝旧版本；无论采用哪种，不能出现两份成功正式复习覆盖。

一个 assessmentTask 最多关联一个 contributionTarget，作答前固定为某 encounter/turn 或某 camp 委托；普通独立卡片无目标时只改变学习。贡献键按 owner/dataEpoch/learningEventId 唯一，不允许在不同 run 重放同一学习结果。Again 后再做引导练习仍属于同回合，最多授能一次。

短学习 session 与整部 AdventureRun 分开。学习步骤再次到期可以进入下一 session；同一 session 内重复重练不推进 FSRS。新词已用完但还有冒险时显式选择暂停或仅练习，不能在后台制造“新”学习目标满足关卡。

旧数据没有策略/任务/epoch 时按 legacy-v1 解释，补默认元信息不得重算历史。新增本地/服务端格式版本在实施时统一声明；恢复后切换 dataEpoch，旧请求的回执与动作不能重新改变当前数据。

## GM 角色与数据权限

角色授权是待实施扩展：普通注册只能创建 USER；管理员初始化/授予是独立可信操作，不能通过 username=admin 或客户端 role 字段获得权限。GM 浏览器接口仅接受已认证、仍有效的 ADMIN Cookie 会话并校验 CSRF，普通 PAT 不授予 GM 能力。检查实际认证方式，不能因为请求随便带一个 Bearer 前缀就免除 GM 的 CSRF。

GM 可管理账号状态和共享内容，但普通学习/备份 API 的 owner 规则不变；默认不能浏览其他正常用户的私人答案、笔记和备份。跳关、发牌、改变时钟/复习状态仅在独立 sandbox 执行。管理审计写服务端数据库，不是需要提交 Git 的开发日志。详见 [GM 管理](../operations/gm.md)。
