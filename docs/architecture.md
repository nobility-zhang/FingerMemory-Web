# FingerMemory 技术方案

状态：M0 技术提案，2026-11-14。依赖版本在这一天按 npm 页面和项目官方文档核对，实际锁定以 M1 生成的 `package-lock.json` 为准。

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
| TypeScript | 7.0.2 | Apache-2.0 | 领域类型和编译检查；版本在工程创建时固定。 |
| Dexie | 4.4.7 | Apache-2.0 | IndexedDB 封装、索引、事务和版本升级。 |
| dexie-export-import | 4.4.2 | Apache-2.0 | 流式、事务式完整数据库备份和恢复，不另写 IndexedDB 序列化器。 |
| Papa Parse | 5.7.1 | MIT | TSV/CSV 解析、引用和换行边界处理；导入规则由领域层校验。 |
| ts-fsrs | 6.0.0 | MIT | M3 调度器，支持 FSRS-7；开发与构建环境要求 Node 24+，官方文档包含浏览器调度用法。 |
| Vitest | 5.1.0 | MIT | 领域、导入、存储事务和调度适配测试。 |
| fake-indexeddb | 6.2.8 | Apache-2.0 | Node 测试中的内存 IndexedDB；不能替代真实浏览器持久化验收。 |

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

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `appMeta` | `key`, `value` | 应用数据版本、最后备份时间等元数据。 |
| `lexicons` | `id`, `name`, `description`, `createdAt`, `updatedAt`, `archivedAt` | 用户词库；归档不同于删除。 |
| `studyItems` | `id`, `term`, `primaryAnswer`, `acceptedAnswers`, `normalization`, `createdAt`, `updatedAt` | 可被检索、练习和调度的学习目标。 |
| `lexiconItems` | `[lexiconId+studyItemId]`, `lexiconId`, `studyItemId`, `order`, `sourceHints` | 词库成员和顺序；同一学习目标可出现在多个词库。 |
| `notes` | `id`, `studyItemId`, `content`, `createdAt`, `updatedAt` | 个人笔记，引用学习目标而不复制词义或复习状态。 |

### M2/M3 再增加的契约

| 表 | 用途 |
| --- | --- |
| `practiceSessions` | 模式、状态、配置快照、开始/结束时间和恢复位置。 |
| `practiceAttempts` | 刺激、响应、结果、提示/显示答案/放弃标记和发生时间。 |
| `reviewStates` | 每个学习目标唯一一份 FSRS 状态、模型版本和下次到期时间。 |
| `reviewLogs` | 不可变正式复习日志，`submissionId` 唯一。 |

时间点统一保存为 UTC epoch 毫秒，展示和“学习日”按用户当前时区转换。排序同时使用显式顺序和稳定 ID，不依赖对象插入顺序。

### 数据一致性

- 词库成员不得引用不存在的学习目标；删除学习目标前必须处理成员关系、笔记和会话引用。
- 正式复习使用 IndexedDB 事务同时写入 `reviewStates` 和 `reviewLogs`。
- `reviewLogs.submissionId` 建立唯一索引；重复提交在事务内检测并返回已有结果，不再次调用调度推进。
- `reviewStates` 保存 `schedulerPackageVersion` 和 `fsrsModelVersion`。升级库或模型不能静默重排已有卡片。
- 备份和恢复使用完整数据库边界；恢复开始前保存当前数据，校验或写入失败时整体回滚。

## 导入与迁移

### 新版 TSV

- 默认分隔符是字面制表符 `\t`，使用 Papa Parse 处理引用、空字段和换行。
- 第一列是词面，第二列是主释义，其余列是提示；导入前预览有效行、空行、重复项和错误行。
- 导入采用“解析预览 -> 用户确认 -> 单事务提交”，取消和解析失败都不改变数据库。
- 重复项不静默覆盖。用户明确选择跳过、合并或作为独立学习目标后再提交。

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

## 复习调度

- `scheduler/` 是唯一可以调用 ts-fsrs 的边界；模式接收命令结果，不直接计算稳定度、难度或到期日。
- M3 通过测试夹具固定输入时间、评分和模型参数，验证相同输入得到稳定结果；只断言能反映失败路径的字段，不复制整个第三方输出对象。
- 评分使用 Again/Hard/Good/Easy 显式映射；输入正确性、输入速度、动画结果和提示状态只形成作答事实或正式复习门禁，不直接代替用户评分。
- 正式复习门禁、提示语义和同会话重复规则未确认前，不编写会写 `reviewStates` 的接入代码。

## 模式插件边界

第一版插件是随项目构建、可信、同源的 TypeScript 模块，不是安全沙箱。最小契约：

- `id`、显示名、版本、能力声明和默认配置。
- `setup(context)` 注册 UI 挂载点、事件订阅和配置；`start/stop` 管理一次会话生命周期。
- 插件只能通过核心查询取题、通过核心命令提交练习或正式复习。
- 返回 `dispose()`，必须清理键盘监听、定时器、`requestAnimationFrame`、观察器、音频和 DOM 订阅。
- 注册表拒绝重复 ID、未知生命周期和不允许的持久化键；禁用插件后保留数据但停止事件和资源。

远程安装、动态 `eval`、第三方脚本和跨站 iframe 不属于第一版。

## 本地数据与安全

- 新版没有账号密码和会话服务；本地数据仍可能被同源脚本、浏览器扩展或共享设备读取。
- 不引入第三方分析脚本、远程字体或默认网络请求。音频等联网能力以后单独评估。
- 导出备份默认是未加密文件，UI 必须说明其包含个人词库和笔记。
- 请求浏览器持久存储只能降低被自动清理的概率，不能承诺永不丢失；定期备份是恢复手段。
- 所有用户文本按纯文本渲染；禁止 `v-html` 注入词条或笔记。

## 验证策略

- 纯领域测试：导入规范化、正式复习门禁、队列上限和提交幂等。
- IndexedDB 集成测试：Dexie 事务、索引、版本升级、备份恢复和失败回滚。
- 浏览器测试：真实 Chromium 中创建数据、刷新、关闭重开、导出、恢复和会话中断；不能只用 mock 作为持久化结论。
- 构建检查：TypeScript、静态检查、单元测试和 Vite 生产构建；静态产物不得要求 Node 运行。
- 高风险变化先备份夹具，再迁移和恢复验证；测试通过只证明实际覆盖范围，不替代人工检查可读性和交互。
