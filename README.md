# FingerMemory

面向个人使用的键盘词汇练习工具，正在按 Vue 3、TypeScript、Vite 和本地优先存储方案重构。

当前已完成本地数据库、FSRS 核心、词库管理、TSV 导入预览和首个歌词式滚动模式。旧版实现已归档到 Git tag `pre-refactor-2026-11-14`，不再保留在 `main` 工作树中。

新版规划入口：

- [产品说明](docs/product.md)
- [技术方案](docs/architecture.md)
- [进度与任务](docs/progress.md)

开发环境计划使用 Node.js 24；生产版本将构建为静态文件，不依赖 Node 或旧后端服务。

## 开发

```sh
cd web
npm ci
npm run dev
```

验证入口：

```sh
npm test
npm run check
npm run build
```
