# FingerMemory

面向个人及少量用户的键盘词汇练习工具。`1.0.0` 本地模式保持可用；2.0 正在建设“中世纪异世界伙伴陪伴的学习 JRPG”，并保留 Java + MySQL 多用户账号、跨设备 Web/CLI 和独立 Anki 式卡片。当前 G0 已保护并提交多用户、在线复习与旧游戏外壳基线；莉娅、瑟琳、旅店、Three.js 冒险和第一章仍待实施。

## 功能

- 本地词库、词条、学习目标、笔记和纯文本 TSV 导入。
- 新词与到期复习上限、暂停队列、本地学习日和四档评分预计间隔。
- FSRS-7 正式复习状态、不可变复习日志、提交 ID 幂等和状态/日志原子事务。
- 歌词滚动、键盘对照、弹幕和卡片雨四种可信内置模式。
- 正式复习诊断、多模式共享状态、应用内随机提醒、免打扰、稍后和每日上限。
- 完整 JSON 备份、校验预览、原子恢复、v3 备份迁移和浏览器存储健康检查。
- Spring Boot 4.1.2 多用户 API、MySQL 8.4、Spring Session JDBC、CSRF 和私人词库归属。
- 隔离 FSRS-7 worker、在线学习日队列、服务端会话、正式复习幂等/版本事务和旧版本冲突。
- 本地 v4 迁移预览、稳定批次 ID、用户范围 ID 映射、用户级账号备份与原子恢复。
- 已提交的“二次元学园科幻训练游戏”大厅、训练、评分和结算；它是迁移基线，不是新的异世界 JRPG 验收结果。
- 统一快捷键状态机、评分数字键上下文、提示/揭晓/跳过练习门禁、专注模式和快捷键帮助。

G1-01 与 G1-02 已交付莉娅、瑟琳各六表情，暖灯旅店背景、双伙伴切换以及训练/暂停/Again/结算中的陪伴对白。两位伙伴都完成真实账号队列与三档浏览器视口验收。经典卡片的独立新入口和第一章探索/遭遇仍待实施；训练提交页暂时保留为迁移中的经典学习流程。

## 环境

- Node.js `24.19.0` 或满足 `package.json` 的 Node 24。
- npm `12.0.2`。
- 目标浏览器为现代桌面 Chromium；当前真实浏览器验收使用 Edge Chromium。

## 开发

```sh
cd web
npm ci
npm run dev
```

默认开发地址为 `http://127.0.0.1:5173/`。

账号模式需要 Java 21 和项目 `server/`。本机没有 Maven 时使用 Maven Wrapper 或固定 Maven 容器：

```sh
docker compose -f server/compose.test.yaml -f server/compose.dev.yaml up -d mysql fsrs-worker
cd server
./mvnw spring-boot:run
```

测试 MySQL 默认只在 Compose 内网提供，具体环境变量见 [server/README.md](server/README.md)。

P2 跨客户端验收（需要 API 和 worker 已运行）：

```sh
cd web
npm run test:p2-cross
```

## 验证

```sh
cd web
npm test
npm run check
npm run build
```

生产构建写入 `web/dist`。静态服务器直接发布该目录即可，不要求重写规则或服务端接口。

## 数据

- 数据保存在当前浏览器的 IndexedDB，概括为 schema/data format v4。
- 当前版本可导入 v3 备份并在写入前完成迁移校验；破坏性升级前仍应主动下载完整备份。
- 备份是不加密 JSON，包含私人词库和笔记，不应上传到不可信位置。
- 申请浏览器持久存储只能降低被清理概率，不能代替定期备份。

## 文档

- [产品说明](docs/product.md)
- [技术方案](docs/architecture.md)
- [进度与任务](docs/progress.md)
- [1.0.0 发布说明](docs/release.md)
- [OpenAPI 契约](docs/openapi.yaml)
- [FSRS-7 兼容性](docs/fsrs-compatibility.md)
- [本地数据迁移](docs/account-migration.md)
- [2.0 视觉与快捷键](docs/ui-direction.md)

旧版实现归档在 Git tag `pre-refactor-2026-11-14`，不在当前工作树保留。
