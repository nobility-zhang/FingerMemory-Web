# 系统架构概览

FingerMemory 包含可独立运行的本地学习模式，以及 Java 服务提供的多用户账号模式。当前已有旅店与伙伴呈现；标准翻面卡片、Phaser 卡牌战斗和可导入冒险包仍按 [开发路线](../development/roadmap.md) 实施。

## 系统边界

```text
Vue Web / Node CLI
  ├─ 本地模式 → 本地学习服务 → IndexedDB / CLI 本地适配
  └─ 账号模式 → Java API → MySQL
                    └─ 固定 FSRS worker（纯计算，内部网络）

Vue/DOM（输入、对话、评分、可访问控件）
  ↔ Phaser 最小音频桥（战斗 Scene 待实施）
  ↔ 游戏应用服务与只读状态快照
```

本地与账号数据不自动双向同步。账号模式的学习和游戏事实以服务端为准；GM 是同一 Java 服务中的受权管理边界，调试只能使用独立 sandbox；动画、音效和客户端自报结果不能直接覆盖正式状态。CLI 通过同一 API 读取账号状态，以文字代替图形表现。

## 主要技术与职责

| 部分 | 采用技术 | 职责与取舍 |
| --- | --- | --- |
| Web | Vue 3、TypeScript、Vite | 原生输入、界面与构建；既有工具链继续复用 |
| 本地存储 | Dexie / IndexedDB、Zod | 事务、升级、快照和输入校验 |
| 学习调度 | 固定 ts-fsrs / FSRS-7 | 复用成熟算法，见 [兼容决策](fsrs.md) |
| 账号服务 | Java 21、Spring Boot、MyBatis | 认证、资源归属、会话、版本检查与事务 |
| 数据库 | MySQL、Flyway、Spring Session JDBC | 用户与学习数据、版本化迁移、会话持久化 |
| 游戏（最小音频桥 + 纯规则） | Phaser + Vue | 已接入单 Game 生命周期、翻面音效和 `card-battle-v1` 纯规则；Java 命令与战斗 Scene 待实施 |
| CLI（待迭代） | Node.js + TypeScript | 在线学习、文字冒险与独立本地模式 |

具体依赖版本以 [Web 清单](../../web/package.json)、锁文件与 [Java POM](../../server/pom.xml) 为准；文档不维护另一张容易过期的补丁版本表。新依赖检查维护、许可、真实用途与工具链兼容，避免浮动 latest 或运行时 CDN。

## 代码导航

| 目录 | 当前职责 |
| --- | --- |
| `web/src/app` | 应用壳、账号和词库/数据工作区 |
| `web/src/core`、`session`、`scheduler` | 学习契约、会话状态与算法适配 |
| `web/src/storage`、`import`、`api` | 本地持久化、词条解析、在线请求边界 |
| `web/src/inn`、`companions`、`game` | 旅店、伙伴和已有在线训练外壳 |
| `web/src/modes` | 旧模式兼容；按计划逐步退出新建入口 |
| `web/src/assets` | 成品角色/背景等素材及来源，二进制按 LFS 规则 |
| `web/worker` | 与本地复用的固定 FSRS 纯计算入口 |
| `server/src/main/java/com/fingermemory/server` | 按 auth、user、lexicon、learning、migration、backup 等业务分包 |
| `server/src/main/resources/db/migration` | 已生效迁移不可重写，新变更追加版本 |
| `web/scripts`、`server/src/test` | 可重复使用的开发工具与测试，输出放忽略目录 |

不为目录整齐进行一次性全仓移动。重点拆分与职责要求见 [开发规范](../development/README.md)；新游戏模块在实际实现时引入，禁止先搭空框架。

## 详细设计的唯一位置

- [学习与数据一致性](learning.md)：实体、表、认证、调度事务、学习规则 v2。
- [游戏运行时](game-runtime.md)：Phaser/Vue 分工、卡牌状态、音效与生命周期。
- [内容包](content-pack.md)：词条与故事格式、安装升级、资源边界。
- [数据迁移](../operations/data-migration.md)：本地/账号、旧格式和备份恢复。
- [OpenAPI](../api/openapi.yaml)：客户端共享接口；planned 路径不能假定已存在。
- [GM 管理](../operations/gm.md)：默认管理员初始化、角色权限、内容发布与隔离测试沙盒。

## 部署边界

本地 Web 可发布静态产物；账号模式使用同源 Web/Java、MySQL 和内部 FSRS worker。宿主机无需为 worker 单独安装 Node；固定容器有独立健康检查与超时。秘密通过受控配置注入，未经指令不推送、公开部署或执行生产迁移。实际命令和环境变量由 [server/README.md](../../server/README.md) 维护。
