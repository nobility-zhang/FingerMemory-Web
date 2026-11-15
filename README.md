# FingerMemory

面向个人使用的本地优先键盘词汇练习工具。当前版本 `1.0.0`，使用 Vue 3、TypeScript、Vite、Dexie 和 ts-fsrs，构建后是无需 Node 或旧后端服务的静态站点。

## 功能

- 本地词库、词条、学习目标、笔记和纯文本 TSV 导入。
- 新词与到期复习上限、暂停队列、本地学习日和四档评分预计间隔。
- FSRS-7 正式复习状态、不可变复习日志、提交 ID 幂等和状态/日志原子事务。
- 歌词滚动、键盘对照、弹幕和卡片雨四种可信内置模式。
- 正式复习诊断、多模式共享状态、应用内随机提醒、免打扰、稍后和每日上限。
- 完整 JSON 备份、校验预览、原子恢复、v3 备份迁移和浏览器存储健康检查。

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

旧版实现归档在 Git tag `pre-refactor-2026-11-14`，不在当前工作树保留。
