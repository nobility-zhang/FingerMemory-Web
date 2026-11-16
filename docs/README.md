# 文档导航

FingerMemory 的长期文档按产品、架构、接口、开发、运维和版本划分。每个主题只维护一份权威说明；运行日志、截图和会话交接不属于文档站。

## 从哪里开始

| 你想了解什么 | 阅读入口 |
| --- | --- |
| 产品怎么玩、角色与英语学习如何结合 | [产品设计](product/overview.md) |
| 角色素材、服装、布局、音效和快捷键 | [视觉与交互](product/experience.md) |
| 当前系统由什么组成、功能放在哪里 | [架构概览](architecture/overview.md) |
| 数据模型、账号隔离、正式评分与事务 | [学习与数据一致性](architecture/learning.md) |
| FSRS 模型和内部计算服务的选择 | [FSRS 适配](architecture/fsrs.md) |
| Phaser、卡牌规则运行、剧情检查点 | [游戏运行时](architecture/game-runtime.md) |
| 一本词库如何成为可安装的冒险 | [内容包格式](architecture/content-pack.md) |
| 请求、响应和当前可用的 API | [OpenAPI](api/openapi.yaml) |
| 如何写出可维护代码、如何提交 | [开发规范](development/README.md) |
| 立绘、音效与 Git LFS | [资源管理](development/assets.md) |
| 接下来的实施顺序与验收要求 | [开发路线](development/roadmap.md) |
| 导入旧数据、账号迁移、备份恢复 | [数据迁移](operations/data-migration.md) |
| 默认管理员、GM 页面与隔离测试 | [GM 管理](operations/gm.md) |
| 旧 1.0.0 版本的实际范围 | [版本说明](releases/1.0.0.md) |

项目启动看 [仓库 README](../README.md)，Java 服务命令看 [服务端 README](../server/README.md)。新开发会话先读根 [AGENTS.md](../AGENTS.md)；如果存在，再读本地忽略的 `.local/handoffs/current.md`。本地交接缺失时直接通过 Git、代码和开发路线恢复，不依赖已删除聊天。

## 维护规则

- 产品规则写在 product；技术实现边界写在 architecture；HTTP 字段以 OpenAPI 为准，避免三处复制接口定义。
- 新能力的状态明确为当前实现或待实施；不能把规划当作已经完成的功能。
- 优先更新已有主题，不按日期、会话或任务轮次不断创建新方案。需要增加文件时，先在本导航说明职责。
- 相对链接必须随移动更新；目录中的长期 Markdown 和 OpenAPI 应有导航入口。
- roadmap 是实施路线，不是执行流水账。实际命令输出、现场端口、截图、失败日志和逐轮交接放在忽略的 `.local/`。
- 文档移动通常不要求运行全套业务测试；应检查链接、旧路径引用、契约文件未变与工作区差异。
