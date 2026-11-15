# FingerMemory 技术方案

状态：2026-11-15 异世界 JRPG 规划更新。P0–P3 可复用基线已保存到本地提交 `2fc973d` 与 `fba6e95`；原验证记录见 progress.md。以下新增 G 阶段规格尚未实现，保留 Java/MySQL、内部固定 FSRS worker 与 v1.0 本地模式。

## 技术选择

### 运行形态

- 本地模式继续使用 Vue 3、IndexedDB 和本地 TS-FSRS，生产可独立发布 `web/dist`。
- 账号模式使用一个 Java 21 / Spring Boot 4.1.2 业务服务、一个 MySQL 8.4 LTS 数据库和同源 Web；Web/CLI 不使用第二套调度规则。
- Node/npm 是 Web 开发构建和后续 CLI 工具链；生产服务器不要求宿主机安装 Node。P2 的内部 TS 调度 worker 仅作为固定容器运行。
- 新代码放在 `server/` 和 `web/`。旧实现已完整归档到 Git tag `pre-refactor-2026-11-14`，不再保留在 `main` 工作树中。
- `docs/` 是新版唯一权威文档目录；旧原型、后端、SQL 和说明只通过 Git tag 查阅，不复制进新架构。

### 依赖候选

| 组件 | 候选版本 | 许可证 | 用途与要求 |
| --- | --- | --- | --- |
| Vue | 3.5.45 | MIT | Composition API 和单文件组件。 |
| Vite | 8.3.8 | MIT | 本地开发、构建和静态产物；开发 Node 要求 20.19+ 或 22.12+。 |
| @vitejs/plugin-vue | 6.0.10 | MIT | Vue SFC 编译。 |
| TypeScript | 6.0.3 | Apache-2.0 | 领域类型和编译检查；锁定在 `typescript-eslint` 当前支持的 `<6.1.0` 范围内。 |
| Dexie | 4.4.7 | Apache-2.0 | IndexedDB 封装、索引、事务和版本升级。 |
| Papa Parse | 5.7.1 | MIT | TSV/CSV 解析、引用和换行边界处理；导入规则由领域层校验。 |
| @types/papaparse | 5.5.2 | MIT | Papa Parse TypeScript 声明，只属于开发依赖。 |
| ts-fsrs | 6.0.0 | MIT | M3 调度器，支持 FSRS-7；开发与构建环境要求 Node 24+，官方文档包含浏览器调度用法。 |
| Zod | 4.6.7 | MIT | 备份快照运行时校验；领域对象和快照类型仍由 TypeScript 约束。 |
| Vitest | 5.1.0 | MIT | 领域、导入、存储事务和调度适配测试。 |
| fake-indexeddb | 6.2.8 | Apache-2.0 | Node 测试中的内存 IndexedDB；不能替代真实浏览器持久化验收。 |
| jsdom | 30.1.3 | MIT | M1-01 组件壳 DOM 验证；后续涉及 IndexedDB 和键盘的真实行为仍在 Chromium 中验证。 |
| @lucide/vue | 1.73.0 | ISC | P3 游戏界面工具、状态和导航图标；按钮保留可访问名称。 |
| Spring Boot | 4.1.2 | Apache-2.0 | Java 21 多用户 API、Spring Security、会话和 Actuator。 |
| MyBatis Boot Starter | 4.1.0 | Apache-2.0 | 唯一关系持久层；显式 SQL、归属条件和版本更新。 |
| MySQL | 8.4.13 | GPLv2 with FOSS exception | 账号、偏好、词库和后续学习数据的关系数据库。 |
| Flyway | Boot 4.1.2 管理 | Apache-2.0 | 版本化数据库迁移。 |
| Spring Session JDBC | Boot 4.1.2 管理 | Apache-2.0 | 登录会话持久化，不引入 Redis。 |
| Bucket4j | 8.22.0 | Apache-2.0 | 登录/注册本地令牌桶限流。 |
| Caffeine | Boot 4.1.2 管理 | Apache-2.0 | 有界、过期的限流桶缓存。 |

资料入口：

- [Dexie documentation](https://dexie.org/docs/)
- [Dexie export/import](https://dexie.org/docs/ExportImport/dexie-export-import)
- [Papa Parse](https://www.npmjs.com/package/papaparse)
- [ts-fsrs quick start](https://open-spaced-repetition.github.io/ts-fsrs/guide/quick-start)
- [ts-fsrs repository and license](https://github.com/open-spaced-repetition/ts-fsrs)
- [Vite getting started](https://vite.dev/guide/)

### 依赖原则

- `package.json` 使用精确版本，提交 `package-lock.json`；不使用 `latest`、浮动标签或 CDN 运行时依赖。
- Dexie Cloud 不采用；账号模式需要自建 Java/MySQL 服务，不使用浏览器云数据库或第三方同步。
- ts-fsrs 只引入调度包，不引入参数训练/WASI binding。M3 先做浏览器集成测试，再确认模型和参数；若失败，保留同一存储契约并重新评估版本，不自行实现 FSRS。
- M1 不提前引入 UI 组件库。先建立可维护的领域和交互基础设施，视觉组件在真实页面需求出现后选择。

## 目录与模块边界

```text
web/
  src/
    app/                 # 路由、页面壳、应用启动和全局错误边界
    core/                # 领域类型、命令、查询、策略和事务边界
    storage/             # Dexie schema、repository、升级和备份适配
    import/              # TSV 与旧格式只读适配器
    session/             # 会话、队列快照、恢复和作答状态机
    scheduler/           # ts-fsrs 适配器；唯一正式复习状态入口
    modes/               # M2 起的模式实现与 M5 插件注册表
    ui/                  # 可复用视图组件
  tests/                 # 单元、IndexedDB 集成和浏览器验收
server/
  src/main/java/         # controller/application、MyBatis、安全和运维命令
  src/main/resources/    # application.yml、Flyway 和静态 Web 构建产物
  src/test/              # 真实 MySQL 集成和 FSRS 兼容夹具
  compose.yaml           # 单服务生产拓扑模板
docs/                    # 产品、架构、进度、发布和迁移文档
docs/openapi.yaml        # Web/CLI 唯一 API 契约
```

本地模式依赖方向为 `ui/modes -> core -> storage/scheduler`。账号模式为 `Web/CLI -> OpenAPI -> controller/service -> MyBatis`；Vue 组件不能到处散写请求，页面也不直接管理 Dexie 表。

## 数据模型

### M1 核心表

M1-02 落地 schema v1；当前已通过 Dexie v4 迁移到词条、学习目标、练习会话、练习、复习状态和日志分离的结构，并补齐会话队列来源。定义位于 `web/src/storage/database.ts`，页面只接收 repository 接口，不直接访问 Dexie 表。

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
- 开始会话前，仓储按浏览器当前 IANA 时区计算本地学习日，将到期复习按 `dueAt` 优先排列，再补入从未产生正式复习状态的新词。
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
| `fm_user_data` | 每个用户一行 schema/data format v4 快照，保存词条、学习目标、笔记、练习、会话、正式状态和日志；事务内 `SELECT ... FOR UPDATE`，`data_version` 单调递增。 |
| `fm_review_receipt` | 用户范围正式复习 submissionId 唯一回执，保存请求指纹和第一次响应。 |
| `fm_reward_event` | 用户、事件类型和事件 key 唯一；正式复习奖励与状态/日志同事务，导入历史标记为不可奖励。 |
| `fm_migration_receipt` | 用户范围迁移批次唯一回执。 |
| `fm_import_id_map` | 迁移批次内原 ID 到目标 ID 的稳定映射。 |
| `fm_backup_receipt` | 用户范围恢复幂等回执。 |

`V3__backfill_user_data.sql` 为 P1 升级前已有账号回填空 v4 快照，避免升级后首次并发访问竞争创建同一用户行。

选择用户行锁加紧凑 v4 快照，而不是为个人规模拆成大量热表：它让会话、日志链、状态和迁移映射共享一个清晰事务边界，也直接复用客户端既有 v4 校验模型。`fm_lexicon` 仍是独立词库投影；导入批次通过 `import_batch_id` 隔离同名和 ID 空间。后续规模证据需要拆表时，必须保持现有 API、回执和版本语义。

## API 与认证边界

- 唯一 API 契约是 `docs/openapi.yaml`；当前 P2 实现 auth、lexicons、words、queue、sessions、formal reviews、migration 和 account backup，其余路径明确标记 planned。
- Web 同源使用 HttpOnly `FM_SESSION`、SameSite=Lax、CSRF header；不得把会话令牌存 localStorage。
- CLI 后续使用 Bearer 个人访问令牌；服务端只保存摘要，CSRF 只对 Bearer 请求豁免，Cookie 请求不被全局关闭保护。
- 所有资源归属来自认证 principal；路径中的 userId 不被接受。跨用户读取返回 404，避免泄漏对象存在性。
- 登录/注册由 Bucket4j 按 IP 和用户名限流；日志不得输出密码、token、Cookie 或完整凭据对象。
- BCrypt 密码按 UTF-8 字节限制为最多 72 字节，避免算法静默截断；停用账号的既有会话在下一次 API 请求时被拒绝并失效。
- 会话由 Spring Session JDBC 持久化；学习会话保存在用户 v4 快照中，以 `dataVersion` 和会话 `version` 双重 CAS；新建登录不会废弃其他设备会话。

## 正式复习服务边界

P0 核查结论详见 `docs/fsrs-compatibility.md`：当前没有通过 FSRS-7/34 权重对照的 JVM 调度器。

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
- 现有 legacy-v1 抄写门禁以 `answerRevealed` 拒绝正式复习；G 阶段按下文学习规则 v2 区分检查答案与事先曝光。IME 组合期间的 Enter 不提交。
- 备份和恢复使用完整数据库边界；恢复开始前保存当前数据，校验或写入失败时整体回滚。
- 备份是版本化 JSON `StorageSnapshot`，包含全部业务表和 `appMeta`。选择该格式而不是二进制数据库转储，是为了在覆盖前执行 Zod 结构校验、跨表引用校验和未来版本迁移；不手写 IndexedDB 底层序列化。
- TSV 导入以 `appMeta` 中的批次回执保存幂等结果；同一批次与同一输入重复提交返回第一次结果，同批次不同输入报冲突。
- 每个学习目标最多保存一条笔记；保存前确认学习目标存在，空内容清除笔记。笔记与词条、会话和调度状态分离。

## 导入与迁移

### 新版 TSV

- 默认分隔符是字面制表符 `\t`，使用 Papa Parse 处理引用、空字段和换行。
- 第一列是词面，第二列是主释义，其余列是提示；导入前预览有效行、空行、重复项和错误行。
- 导入采用“解析预览 -> 用户确认 -> 单事务提交”，取消和解析失败都不改变数据库。
- 存在无效记录时禁止确认，避免一次坏文件产生部分导入。
- 当前重复项按目标词库内“规范化英文拼写 + 主释义”精确识别，不静默覆盖。用户逐条选择跳过或作为独立学习目标后再提交；跨词库合并不在 M1-04 实现。
- 同一拼写但不同主释义仍建立独立学习目标，不据此确认 A4 的长期共享规则。
- 导入词条、学习目标、词库成员关系和批次回执在同一个 Dexie 事务中写入；任一行失败时全部回滚。

### 旧 CLI 数据

旧 CLI 的实际行为是：配置写 `separator: '\t'`，运行时却改成四个空格；卡片缓存是逐行 JSON，会话历史是 `cache/<词库>/<词库>_<组号>.json` 加 `history.log` 的行号/模式信息。兼容策略：

- 新导入器默认不复制这个替换行为，标准 TSV 的制表符保持原义。
- 若用户选择“旧 CLI 四空格格式”，使用独立适配器并显示预览，不能自动猜测和混合分隔符。
- 旧 JSON 卡片映射 `word`、`describe`、`clue[]`；旧历史只提供恢复提示，不直接视为正式复习日志。
- 两个仓库当前都没有可读取的真实词表、缓存、历史或本地数据库文件；在没有样本前不宣称已完成旧数据迁移。

### 旧 Web 数据

- `fm.sql` 只是 2021 年演示种子，包含 21 个演示用户、23 个单词和明文演示密码；Mongo 内容与 Redis 会话没有仓库内快照。
- 新版不直接读取旧运行中的数据，也不复制明文密码、用户账号或会话表。
- 若以后找到个人 MySQL/Mongo 导出，先新增只读、固定输入的迁移适配器和脱敏夹具，再迁移词库、词条、释义、提示和笔记；旧浏览进度不作为 FSRS 历史。

### 本地 v4 到账号

P2 已实现 `docs/account-migration.md` 的策略：上传前由界面触发本地备份下载、稳定批次 ID、用户范围 ID 映射、v4 引用/状态/日志链校验、追加为独立导入集合、成功不删除本地数据、导入历史不发放奖励。

### 本地备份

- 导出在一个只读 Dexie 事务中读取所有表，生成带 `snapshotVersion`、`schemaVersion`、`dataFormatVersion` 和 UTC 导出时间的 JSON 文件。
- 文件名使用 UTC 时间，内容使用两空格格式化 JSON 并以换行结尾；文件不加密，界面明确提示其包含私人学习数据。
- 备份下载本身不修改数据库；导出的表计数用于确认真实读取范围，不能替代后续恢复验收。
- 恢复先执行 Zod 结构校验和跨表引用校验，再显示版本、导出时间和表计数；用户明确确认覆盖后才开始写事务。
- 恢复在一个 Dexie 读写事务中清空并重新写入全部表；任意写入失败会回滚到恢复前数据。非法 JSON、未来版本和损坏引用在写入前失败。
- 当前导出 schema/data format v4；导入兼容 v3 快照，在写入前补齐缺失的队列来源并升级 appMeta 的数据格式标志，再执行跨表引用校验。

## 复习调度

- `scheduler/` 是唯一可以调用 ts-fsrs 的边界；模式接收命令结果，不直接计算稳定度、难度或到期日。
- 当前锁定 `ts-fsrs@6.0.0` 的异步 `DefaultScheduler()`、FSRS-7、`dateChrono`、`scheduler.newCard()` 和 `scheduler.review({ card, grade, now })` API；未使用已废弃的 v5 类与字段。
- FSRS 卡片的时间点转换为 UTC 毫秒快照，调用库时再转换为 `Date`；状态保存 `stability`、`stabilityFast`、`difficulty`、`dueAt`、`lastReviewAt`、`state`、`scheduleStatus`、`reps`、`lapses`、`scheduledDays` 和 `learningStep`。
- 调度配置快照保存模型/包版本、权重、期望留存率、学习/重学步骤、最大间隔、是否启用 fuzz 等字段；版本或配置不一致时拒绝继续推进已有状态。
- M3 通过测试夹具固定输入时间、评分和模型参数，验证相同输入得到稳定结果；只断言能反映失败路径的字段，不复制整个第三方输出对象。
- 评分使用 Again/Hard/Good/Easy 显式映射；输入正确性、输入速度、动画结果和提示状态只形成作答事实或正式复习门禁，不直接代替用户评分。
- 评分前通过只读 `previewReviewIntervals` 使用当前卡片、锁定配置和同一个评分时间计算四档预计到期；预览不写状态或日志，最终提交仍由 `submitReview` 原子推进。
- 当前实现为 legacy-v1 的隐藏/无提示门禁；新增卡片自评和明确遗忘路径按下文 assessmentPolicyVersion=2 迁移，不能只改 UI。
- 旧 A1 是历史策略，不再作为翻面卡片的限制；A3 同一目标同一会话最多一次正式复习的约束继续有效。旧日志保持原始语义，不回算。
- 同会话上限由 `submitReview` 在状态与日志事务内再次检查，不依赖 UI 按钮状态；重复的 `submissionId` 仍先按幂等返回原结果。
- 诊断查询以只读事务汇总每个学习目标唯一的状态、词条和全部不可变日志，并核对相邻日志前后卡片及最新日志与当前状态；异常返回可定位的问题文本，不修改数据。
- 会话工作台在模式组件之外读取并展示队列目标的唯一正式状态；歌词、键盘及后续动画模式只替换呈现，不能缓存或另建掌握度。

## 模式插件边界

M5 已落地随项目构建、可信、同源的 TypeScript 插件注册表，不声称具备安全沙箱。契约：

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

## Web 视觉与快捷键

目标详见 `ui-direction.md`：中世纪异世界、多位原创伙伴与可交互 JRPG；旧学园科幻方向停用。现有 `GameWorkspace`、API 客户端与快捷键解析器可复用边界，不直接以更换背景和人物文件宣布新体验完成。

- 现有大厅→训练→结算与已验证的请求/焦点保护作为迁移基线；新流程加入旅店、地图、对白、遭遇与检查点。
- Vue/DOM 管理输入、卡片、显式评分、对白和菜单；Three.js 管理空间呈现与场景交互，不管理复习事实。
- 旧 `PracticeModeId` 四值是历史呈现策略，增加的叙事内容不硬编码进该枚举；引入 `assessmentType` 与 `encounterType`，保留旧会话的可读取和可退出路径。
- 现有 `/sessions/{id}/practice` 的 guided-practice 语义保留；新增翻面卡片和“没想起”走正式评估流程，不偷改练习接口让所有抄写都推进 FSRS。
- 旧 `web/src/assets/game/` 素材保留到引用迁移完成；新素材按角色、表情、背景和包版本归档，不能先删资源造成已有入口损坏。

## 部署与秘密

- `server/compose.yaml` 使用 MySQL 持久卷、外部密码环境变量、固定镜像、应用健康检查和仅回环端口发布。
- `server/Dockerfile` 多阶段构建 Web 静态产物与 Java JAR，最终 JRE 镜像以非 root 用户运行。
- `server/fsrs-worker/Dockerfile` 把与 Web 共用的 ts-fsrs 6.0.0 调度适配器构建成单文件 Node 制品；worker 不发布宿主端口，只允许应用通过 Compose 内网调用。
- 生产 HTTPS 必须设置 `SESSION_COOKIE_SECURE=true`；应用只监听受控反向代理后的回环端口。
- 未经用户明确授权不推送、不部署、不执行生产数据库迁移。当前没有生产备份/恢复实现，不能把 migrations 写成已完成运维能力。

## 本地数据与安全

- 本地模式不启用账号密码服务；账号模式使用独立 HttpOnly 会话。两种模式的浏览器数据仍可能被同源脚本、浏览器扩展或共享设备读取。
- 不引入第三方分析脚本、远程字体或默认网络请求。音频等联网能力以后单独评估。
- 导出备份默认是未加密文件，UI 必须说明其包含个人词库和笔记。
- 请求浏览器持久存储只能降低被自动清理的概率，不能承诺永不丢失；定期备份是恢复手段。
- 数据视图显示 `StorageManager.estimate()` 的用量和配额，并提供明确触发 `persist()` 的入口；是否授予由浏览器决定。
- 应用内提醒记录保存在 `appMeta['practice.inAppReminder']`：设置包含随机间隔、每日上限、免打扰与稍后分钟数，状态包含学习日、当日次数、下次时间、稍后时间和当日关闭标记。
- 提醒使用单个 `setTimeout` 和纯策略函数；应用隐藏或关闭时没有后台进程，重新打开后按持久化状态恢复。安静时段结束后重新计算，不使用系统通知。
- 所有用户文本按纯文本渲染；禁止 `v-html` 注入词条或笔记。

## 验证策略

- 纯领域测试：导入规范化、正式复习门禁、队列上限和提交幂等。
- IndexedDB 集成测试：Dexie 事务、索引、版本升级、备份恢复和失败回滚。
- 浏览器测试：真实 Chromium 中创建数据、刷新、关闭重开、导出、恢复和会话中断；不能只用 mock 作为持久化结论。
- 构建检查：TypeScript、静态检查、单元测试和 Vite 生产构建；静态产物不得要求 Node 运行。
- 服务端集成：真实 MySQL 8.4、Flyway、Spring Session、MyBatis、MockMvc、CSRF、重登和两用户归属反例。
- 跨端：OpenAPI 契约测试、Web/CLI 同账号状态、并发/幂等、断网和终端恢复在 P2/P5/P6 补充。
- 高风险变化先备份夹具，再迁移和恢复验证；测试通过只证明实际覆盖范围，不替代人工检查可读性和交互。

## G 阶段叙事与玩法扩展

以下为待实施方案，不表示已有 API、表或内容包已经存在。所有扩展保持原账号隔离、FSRS worker、幂等和事务能力。

### 责任划分与模块

按现有结构渐进增加 `web/src/adventure/`（内容图、纯状态机、检查点）、`web/src/scenes/`（Three.js 适配）、`web/src/companions/`（画像/对白/用户偏好）与 `web/src/content/`（包解析、校验和版本）。这只是建议目录，不要求先创建空框架。

```text
导入内容包 → 校验与版本安装 → 内容图/场景描述
                              ↓
用户选择/探索 → AdventureRuntime → Vue 对话/卡片 + Three.js SceneAdapter
                              ↓
评估会话 → 既有学习服务 → 固定 FSRS worker
                              ↓
确认的学习事件 → 同事务故事推进/奖励回执 → 渲染与角色反馈
```

学习核心不依赖 Three.js、角色或特定故事。内容图只能调用已注册的动作；渲染器只接收状态与产生选择意图。画面播放结束不构成事务提交，动画重播不产生新奖励。

### 学习规则 v2 与版本兼容

- `assessmentType` 为 `typed-recall`、`flashcard-self-report`、`guided-practice`；和模式、遭遇、客户端来源分别保存。
- 新评估记录包含 `assessmentPolicyVersion`、展示/尝试/检查答案阶段、是否事先曝光、使用提示、明确“未想起”、评分及持久提交 ID。字段名以定稿 OpenAPI 为准，不将这些示意字段当作现有接口。
- 翻面卡片是 prompt→attempt→reveal-for-check→rate；reveal-for-check 合法后允许四档评分。主动“没想起”进入检查答案并明确 Again。服务端不能将揭晓统一当作弊，也不能只凭客户端一个布尔值跳过阶段检查。
- 拼写回忆独立作答后才揭晓；作答前请求帮助可选择记录失败 Again，再进入 guided-practice。不得先抄正确答案再算 Good/Easy。
- 保持现有同目标同会话一次正式提交；Again 的下一学习步骤按调度新建到期任务/后续会话，而非同会话无限重记。
- 旧记录缺失策略字段时解释为 legacy-v1。新本地 schema/备份版本和服务端数据快照版本按实际实现递增；旧版本读取器遇到未来格式应拒绝写入，不能把新记录误当旧门禁处理。
- 不重放旧日志、不改变 FSRS 模型/权重、不追溯补发剧情或经验。扩展 OpenAPI、Zod、Java 校验器、worker 输入边界测试和 CLI 类型时同步提供新旧 fixture。

### 内容包格式草案

首版 `.fmbook` 是数据与媒体 ZIP，不含可执行插件。目录示意：

```text
manifest.json       # schemaVersion/bookId/contentVersion/engineRange/entryNodeId
vocabulary.tsv      # 稳定 wordKey、term、meaning、acceptedAnswers、hints
bindings.json       # chapter/node → wordKey 或明确的词条分组
story.json          # chapters/nodes/choices/transitions/encounter descriptors
characters.json     # 稳定角色 ID、立绘表情资源和对白引用
assets.json         # 路径、hash、mime、尺寸、来源/许可、fallback
assets/             # PNG/WebP/JPEG、可选 GLB 与音频
```

普通旧 TSV 不强制具备 wordKey，导入器显式分辨“普通词库”与包内格式。格式编码采用 UTF-8；列内数组采用统一、可校验编码，不用无说明的分隔符拼接。

固定节点类型为 dialogue、choice、explore、encounter、camp、chapter-end。首版遭遇类型只有 `rune-bridge`，后续增加 `ward-escort` 等由应用注册的实现。包可配置背景、对白、词组绑定、技能参数和转移，不能执行 JS/Lua、动态 eval、任意 HTML 或自由表达式。

条件使用有界白名单，如 flagEquals、nodeCompleted、encounterResolved；动作使用 setFlag、startEncounter、unlockNode、finishChapter。保存前验证目标节点、资源/角色/词条引用、可达入口与结局、可退出路径和非法自动循环。需要循环重访的营地必须是显式玩家动作，不能无限自动执行节点。

`bookId + contentVersion + contentHash` 识别不可变内容；`wordKey/nodeId/characterId` 不使用数组位置或展示名称作身份。每次安装生成 owner 范围映射，其他账号不能通过包 ID 读取私有素材或存档。包内 wordKey 绑定至现有 studyItemId，复用已有学习目标需要明确映射，不自动按单词拼写合并。

### 包导入、升级与资源边界

- 使用成熟 ZIP 库；限制文件数、压缩包总量、实际解压总量、单资源大小/像素和异常压缩比；拒绝路径穿越、绝对路径、符号链接、重复规范化路径、加密包和可执行脚本。
- 初始可配置预算建议：压缩包 50 MiB、解压总量 200 MiB、1000 文件；结合真实样例验证，超限明确提示。解压时持续计量，不只相信 ZIP 声明。
- 首版拒绝 SVG/HTML 内容资源与外链媒体；GLB 若采用，要阻止未声明的外部 URL/脚本式扩展并限制资源体量。允许的字体/音频格式和许可在样例包确定后列入白名单。
- 内容哈希证明一致性，不证明作者可信；文字仍按纯文本渲染。模型和图片解析使用锁定的成熟库。
- 流程为临时解包→结构/语义/资源校验→导入预览→用户确认→原子安装。任何失败不留下半个词库或可见空章节。
- 本地资源放独立 Blob/内容资源存储；账号资源使用服务器管理的文件目录/卷与元数据，不把 base64 图片塞入现有 fm_user_data JSON。
- 文件先写 staging，内容寻址资源准备完整后事务切换 installed 清单；未安装文件不能被读取，失败回滚元数据并延迟回收孤立资源，不能宣称 MySQL 与文件系统天然同事务。
- 同账号相同包/版本/内容重复导入返回同一安装回执；同版本不同 hash 拒绝并要求作者递增版本。升级先预览词条、节点与存档影响。
- 进行中的 run 固定包版本。无迁移映射时可继续旧版或另开新 run；不得让正在进行的 nodeId 指向不存在的新内容。删除书包可归档并停止新 run，保留独立 FSRS 历史和可导出的故事记录。

### 存档与数据模型

| 实体 | 关键身份与用途 |
| --- | --- |
| ContentPackage / AssetManifest | 不可变包版本、内容 hash、资源白名单、安装状态 |
| UserBookInstallation | owner、包版本、词条映射、导入回执与归档状态 |
| AdventureRun | owner、installation、节点/章节、分支 flags、runVersion、已提交检查点 |
| EncounterState | run/node/encounterId、规则版本、回合、队伍/路障状态、待处理学习目标与行动 |
| CompanionPreference / BondEvent | owner、带队角色、称呼/密度、去重的关系事件 |
| AdventureEventReceipt | owner、eventId、来源学习 submission/attempt、请求指纹和原始结果 |

这些实体与复习状态分离。可复用当前单数据库事务及用户锁策略，但媒体文件不进入大快照；需要新表时用新增 Flyway，不改已运行的 V1–V3。个人内容规模下先做正确性，不为未来开放世界拆微服务。

帐号正式回合流程：Java 验证用户、内容安装、当前遭遇/队列绑定与版本→原事务边界外调用 worker→事务内复核版本/回执→一起写学习状态、日志、故事事件、能量和奖励回执。空/重复事件、旧事件重放和迁移导入不能推进新遭遇。

引导练习可作为遇敌教学，但章节规则必须声明何种练习事件有资格贡献一次行动。计分消费 `(owner, run, encounter, learningEventId)` 去重，同时防止同一动作 ID 改请求体；跨 run 的一次性纪念品按 owner/book/rewardKey 去重。奖励与自评高低无关。服务端不接受客户端自报“杀敌/通关/好感 +100”。

分支选择使用独立幂等 actionId 与 runVersion；请求竞争返回冲突并提供可恢复状态。渲染失败不会撤销成功学习；恢复直接读最新检查点，可以补播或跳过反馈动画，不重跑领域事件。角色表情属于表现状态，私有偏好/关系值属于持久化状态，不能混放。

### 队列与文本降级

FSRS 到期队列优先，章节配置只约束新词来源。遭遇需要的题不足时缩短回合或显式转为教学/营地，不为通关突破日上限。每个任务锁定 wordKey→studyItemId 与内容版本；剧情曝光过的目标不能被同一回合伪装成独立回忆。

每个 explore/encounter 节点提供可读说明、标号选项与动作结果；CLI 和 WebGL 不可用的 Web 使用同一事件/校验协议。CLI `adventure list/start/resume` 消费已提交检查点；跨端不传 DOM、WebGL 状态或动画帧。终端不可复现的纯装饰可跳过，核心分支与学习结果保持一致。

### Three.js 生命周期与性能预算

Three.js 作为懒加载 SceneAdapter 接入；版本与依赖在 G4 锁定并检查当前官方文档。首版采用 WebGL 固定/正交镜头、2.5D 手绘层与少量低复杂度物件；不让用户输入依赖渲染帧。

- Vue 生命周期创建/挂载/暂停/销毁场景，单一渲染循环；后台/暂停/卡片专注时停止非必要更新。
- 明确资源拥有者和缓存引用计数；卸载停止动画、移除监听，释放 geometry、material、texture、render target、controls 和渲染器；共享纹理不能被一个场景提前释放。
- 减少动画采用静态镜头/无震屏；context lost、纹理失败、低性能时保留已提交状态并降级到 DOM/二维地图。
- 初步设计预算：首个可交互旅店按需载入资源不超过 8 MiB，后续章按需加载；目标笔记本 1080p 交互达到 30 FPS 以上、输入到可见反馈 p95 不高于 100 ms；这些是待实测目标，不是承诺。
- DPR 设可配置上限，避免高分屏无界分配纹理。连续进入/离开 10 次后，检查 renderer.info 与监听/定时器不存在随次数线性增长；保留可解释的有限缓存。

参考：[Three.js 资源释放](https://threejs.org/manual/pages/how-to-dispose-of-objects.html)、[Three.js 清理指南](https://threejs.org/manual/pages/cleanup.html)。这些资料只支持资源生命周期要求，不证明本项目性能已通过。

### API 与备份扩展

扩展现有 OpenAPI，而非在前端发明接口。规划资源包括内容包预览/安装/升级、run 创建/读取、action 提交、encounter 读取、assessment 类型与 reveal-for-check/评分，以及伙伴偏好/对话回看。学习评估契约在 G2 定稿，内容/存档路径在 G3 定稿；契约要包含账号归属、策略/内容版本、幂等键、错误和恢复语义。

用户备份升级需覆盖 installation、run、encounter、关系/奖励回执、内容版本引用和资源清单；仅导出 metadata 但丢失私有素材不能称完整恢复。可导出含资源的版本化归档或明确列明的资源旁包，恢复先校验依赖与引用并进行原子可见性切换。旧 v3/v4 备份继续可按原路径迁移，新增故事默认为空，不凭旧复习日志生成历史剧情。详见 `account-migration.md` 新增边界。
