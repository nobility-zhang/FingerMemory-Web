# FingerMemory

面向个人及少量用户的英语学习应用。当前提供本地词库与备份、FSRS 复习、多用户在线学习、
旅店与伙伴、经典闪卡、Phaser/Vue 卡牌冒险，以及内容包续章和结局续作。功能目标与
当前可用能力分开维护，完整设计见 [文档导航](docs/README.md)。

## 当前能力

- 本地词库、笔记、TSV 导入、会话恢复和版本化 JSON 备份。
- FSRS-7 调度、显式评分、提交幂等、状态与日志事务。
- Java/MySQL 账号模式，私人词库、在线会话、本地数据迁移与用户级备份。
- 账号备份 v2 包含战斗、冒险、结局回执、内容版本与私有媒体；恢复切换 epoch 并拒绝旧请求。
- 暖灯旅店、莉娅/瑟琳/菲妮伙伴切换、六表情、陪伴密度和键盘训练流程。
- 独立经典翻面闪卡，支持空格翻面、Again、同会话去重和完整键盘评分。
- `card-battle-v1` 卡牌回合、真实音效、溪桥教学战和三章暖灯冒险。
- `.fmbook` 预览/安装、三选一牌组奖励、剧情角色解锁和账号级回忆回看。
- 潮声海湾支线、莉娅/瑟琳泳装切换、潮汐卡牌战和可静音的海浪环境音。
- 独立内容包“风车邮路”与动态伙伴诺拉，支持包内场景、六表情服装、解锁和伙伴伤害技能。
- 结局完成回执、同书安全检查点续章、按结局匹配续作、前置条件提示和继承 facts 去重。
- `/gm` 管理员账号目录、首次改密门禁、普通/测试账号管理、隔离 sandbox 和脱敏审计。
- GM 内容草稿只读预览、不可变版本发布、共享冒险书架和归档后旧 run 保护。
- 歌词、弹幕、卡片雨已停止新建；旧会话可显式转为经典闪卡或归档，历史仍可读取。

更多内容包/伙伴、CLI 新版和完整故事备份仍按
[开发路线](docs/development/roadmap.md) 推进。

## 环境与资源

Web 使用 Node 24 与 npm，准确要求以 [package.json](web/package.json) 和锁文件为准。账号模式还需要 Java 21 和 MySQL/内部调度 worker，详见 [服务端说明](server/README.md)。

仓库已声明成品二进制素材的 Git LFS 规则。安装 Git LFS 后，在仓库根执行：

```sh
git lfs install --local
git lfs pull
```

当前成品素材已通过 Git LFS 指针转换，但旧提交中的普通 blob 不会自动从历史
消失。新增或更新素材、指针检查和构建要求见
[资源管理](docs/development/assets.md)。工作区必须包含真实图片/声音，不能以
LFS 指针文本运行构建。

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
