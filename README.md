# FingerMemory

面向个人及少量用户的英语学习应用。当前提供本地词库与备份、FSRS 复习、多用户在线学习、旅店和伙伴界面；下一阶段建设独立翻面闪卡与中世纪异世界卡牌 JRPG。功能目标与当前可用能力分开维护，完整设计见 [文档导航](docs/README.md)。

## 当前能力

- 本地词库、笔记、TSV 导入、会话恢复和版本化 JSON 备份。
- FSRS-7 调度、显式评分、提交幂等、状态与日志事务。
- Java/MySQL 账号模式，私人词库、在线会话、本地数据迁移与用户级备份。
- 暖灯旅店、莉娅/瑟琳伙伴切换、表情、陪伴密度和键盘训练流程。
- 歌词、弹幕、卡片雨仍在旧实现中，按路线退出新建入口并保留读档兼容。

真正的 Anki 式翻面评分、新角色方向、Phaser 卡牌战斗、音效、故事包和 CLI 新版仍按 [开发路线](docs/development/roadmap.md) 推进。现有“经典卡片”按钮尚不能代表完整翻面功能。

## 环境与资源

Web 使用 Node 24 与 npm，准确要求以 [package.json](web/package.json) 和锁文件为准。账号模式还需要 Java 21 和 MySQL/内部调度 worker，详见 [服务端说明](server/README.md)。

仓库已声明成品二进制素材的 Git LFS 规则。安装 Git LFS 后，在仓库根执行：

```sh
git lfs install --local
git lfs pull
```

属性规则不等于旧文件已转换；现有素材接入、指针检查和构建要求见 [资源管理](docs/development/assets.md)。工作区必须包含真实图片/声音，不能以 LFS 指针文本运行构建。

## 本地 Web 开发

```sh
cd web
npm ci
npm run dev
```

默认地址为 `http://127.0.0.1:5173/`。本地模式可独立使用；账号模式需要另外启动 Java API、MySQL 和 worker，启动命令以 [server/README.md](server/README.md) 为准。

## 验证与构建

```sh
cd web
npm test
npm run check
npm run build
```

静态产物在 `web/dist`。本地学习能力可以静态发布；账号功能仍需同源后端。服务端和跨客户端验证入口见服务端说明及 `web/package.json`。

## 数据与文档

本地数据属于当前浏览器，账号数据由 Java 服务管理，两者不自动双向同步。迁移前可导出备份，备份文件包含私人词库与笔记；完整规则见 [数据迁移](docs/operations/data-migration.md)。

- [文档导航](docs/README.md)：产品、架构、API、开发、运维与版本入口。
- [开发规范](docs/development/README.md)：职责拆分、可读代码、验证与分段提交。
- [资源管理](docs/development/assets.md)：成品素材、来源、Git LFS 与过程产物隔离。
- [开发路线](docs/development/roadmap.md)：下一阶段目标和验收标准。

会话交接和验收过程保存在忽略的 `.local/`，不进入 Git。旧原型通过 `pre-refactor-2026-11-14` tag 查阅，历史本地版范围见 [1.0.0 说明](docs/releases/1.0.0.md)。
