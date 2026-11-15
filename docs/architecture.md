# FingerMemory 技术方案

状态：M4 歌词式滚动实施基线，2026-11-15。依赖版本按 npm 页面和项目官方文档核对，实际锁定以 `web/package-lock.json` 为准。

## 技术选择

### 运行形态

- 新版是纯静态单页应用，开发期使用 Node 24 和 npm 构建，生产只发布 `web/dist` 静态文件。
- Node/npm 只属于开发、构建、测试工具链；浏览器运行时不需要 Node、Java、数据库或常驻服务。
- 新代码放在 `web/`。旧实现已完整归档到 Git tag `pre-refactor-2026-11-14`，不再保留在 `main` 工作树中。
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

资料入口：

- [Dexie documentation](https://dexie.org/docs/)
- [Dexie export/import](https://dexie.org/docs/ExportImport/dexie-export-import)
- [Papa Parse](https://www.npmjs.com/package/papaparse)
- [ts-fsrs quick start](https://open-spaced-repetition.github.io/ts-fsrs/guide/quick-start)
- [ts-fsrs repository and license](https://github.com/open-spaced-repetition/ts-fsrs)
- [Vite getting started](https://vite.dev/guide/)

### 依赖原则

- `package.json` 使用精确版本，提交 `package-lock.json`；不使用 `latest`、浮动标签或 CDN 运行时依赖。
- Dexie Cloud 不采用；当前目标没有云同步、账号或服务端授权需求。
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
docs/                    # 新版产品、架构和进度文档
```

模块依赖方向为 `ui/modes -> core -> storage/scheduler`。存储和调度适配器不能反向依赖页面或具体模式。

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

### 数据一致性

- 词库成员不得引用不存在的学习目标；删除学习目标前必须处理成员关系、笔记和会话引用。
- 删除或归档词库只影响后续新会话的取词，不删除学习目标、练习、复习状态和日志；未完成会话按队列快照中的学习目标 ID 独立恢复，不依赖词库当前成员关系。“移除词条”表示移除当前词库成员，不硬删除共享学习历史。未完成会话引用的学习目标不能直接移除。
- 正式复习使用 IndexedDB 事务同时写入 `reviewStates` 和 `reviewLogs`。
- `reviewLogs.submissionId` 建立唯一索引；重复提交在事务内检测并返回已有结果，不再次调用调度推进。
- `reviewStates` 保存 `schedulerPackageVersion` 和 `fsrsModelVersion`。升级库或模型不能静默重排已有卡片。
- 同一提交 ID 但请求内容不同必须报冲突，不能返回一个看似成功但语义不同的结果。
- 正式复习时间不得早于该学习目标已有的 `lastReviewAt`；练习记录与正式复习日志分别保存。
- 会话队列必须引用存在的学习目标；当前索引必须对应队列中唯一 `currentStudyItemId`；待评分答案必须属于当前会话。
- 错词重练只向当前会话追加同目标的一次待练项，不新建会话或复习状态；开始新一轮会将其余未完成会话标记为 `abandoned`。
- 看答案抄写使用同一练习记录命令，但 `answerRevealed` 会关闭正式复习门禁；IME 组合期间的 Enter 不提交。
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
- 当前核心已按定稿门禁写入 `reviewStates`：答案必须隐藏、未提示、未揭晓、未放弃且用户显式确认评分。
- A1 已定稿为“本次作答一旦提示、揭晓或放弃就永久失去正式复习资格，必须重新发起干净回忆”；A3 已定稿为“同一学习目标在同一会话最多一次正式复习，第一次成功提交优先”。
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

## 本地数据与安全

- 新版没有账号密码和会话服务；本地数据仍可能被同源脚本、浏览器扩展或共享设备读取。
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
- 高风险变化先备份夹具，再迁移和恢复验证；测试通过只证明实际覆盖范围，不替代人工检查可读性和交互。
