# FingerMemory Server

Spring Boot 4.1.2、Java 21、MyBatis、MySQL 8.4 和 Spring Session JDBC 构成的多用户 API。Web 生产构建与 API 在同一镜像和同源端口提供；正式复习调用 Compose 内网的固定版本 FSRS-7 worker。

## 本地开发

使用开发入口会启动隔离的测试 MySQL/FSRS worker，在忽略目录生成或复用
`.local/secrets/admin.env`，并在 `127.0.0.1:8081` 启动服务：

```sh
cd server
./scripts/dev-server.sh
```

入口只输出凭据文件路径，不输出密码。首次登录用户名 `admin`，密码从该文件读取；
页面 `/gm` 会要求先修改初始密码。不要在日志、聊天或提交中复制密码。

Web 开发服务器代理到该隔离端口：

```sh
cd web
VITE_API_TARGET=http://127.0.0.1:8081 npm run dev
```

`compose.dev.yaml` 只把测试 MySQL/worker 发布到回环端口，不得用于共享或现有数据库。
默认开发端口使用 `8081`，避免误连或覆盖本机已有的 `8080` 实例。

在仓库根目录执行 worker 与前端的制品检查：

```sh
cd web
npm run worker:build
npm run build
```

## 验证

真实 MySQL 集成测试：

```sh
docker compose -f compose.test.yaml run --rm server-tests
```

测试 Compose 会构建并健康等待 `fsrs-worker`、MySQL 和应用测试容器；不会读取生产 secrets 或现有数据库。

仅清理本项目测试资源：

```sh
docker compose -f compose.test.yaml -f compose.dev.yaml down -v
```

## 生产构建

从 `server/` 目录复制 `.env.example` 为本地未跟踪的 `.env`，替换所有假密码后运行：

```sh
docker compose up --build -d
```

应用仅绑定 `127.0.0.1:${APP_PORT}`，应由受控反向代理提供 HTTPS。生产模板默认 `SESSION_COOKIE_SECURE=true`，本地纯 HTTP 验证时才可显式设为 `false`。

数据库迁移由 Flyway 在应用启动时执行。当前没有自动备份或恢复；生产部署前必须另行实现和验证数据库备份。

当前提供用户级 JSON 备份 `GET /api/v1/backup/export` 与预览/原子恢复接口；它不能替代 MySQL 数据库级备份。

## 在线学习 API

- `GET/POST /api/v1/lexicons/{id}/words`
- `GET /api/v1/queue/plan`
- `GET/POST /api/v1/sessions`
- `GET/PATCH /api/v1/sessions/{id}`
- `POST /api/v1/sessions/{id}/responses`
- `POST /api/v1/sessions/{id}/practice`
- `POST /api/v1/sessions/{id}/review-preview`
- `POST /api/v1/sessions/{id}/reviews`
- `POST /api/v1/migrations/local/preview`
- `POST /api/v1/migrations/local`
- `GET /api/v1/backup/export`
- `POST /api/v1/backup/import/preview`
- `POST /api/v1/backup/import`
- `POST /api/v1/content-packages/preview`
- `GET/POST /api/v1/content-packages`
- `GET /api/v1/content-packages/{id}`
- `GET /api/v1/content-packages/{id}/assets/{assetPath}`
- `GET/POST /api/v1/adventures`
- `GET /api/v1/adventures/{runId}`
- `POST /api/v1/adventures/{runId}/encounters`
- `POST /api/v1/adventures/{runId}/actions`
- `GET /api/v1/adventures/companions`
- `GET /api/v1/adventures/recollections`

正式复习以 `Idempotency-Key` 作为 submissionId；先查回执，再校验用户数据、会话和复习状态版本。请求只提交评分和证据，afterCard、reviewedAt 和奖励事件由服务端确定。

`.fmbook` 安装使用 multipart 上传和 `Idempotency-Key`。预览与安装复用同一
manifest/TSV/章节/角色/卡牌/遭遇/资源校验器；相同 owner 与 contentHash 重复安装
返回同一安装，相同包版本不同 hash 冲突。资源元数据与文件按 owner/内容哈希隔离，
生产 Compose 使用 `content-data` 卷。

冒险 run 固定 `installationId + contentVersion + contentHash`，启动时把该版本的
词条导入账号学习快照并创建在线会话。剧情动作、遭遇创建和战斗结算都使用
owner 范围、`Idempotency-Key`、请求指纹、`runVersion` 与不可变事件；遭遇绑定唯一
`card-battle-v1`，只有服务端战斗进入 victory/defeat 后才能推进到对应剧情节点。
仓库内的第一章可编辑源与 LFS 成品包见 `content/warm-lantern/`。

遭遇胜利会固定三选一牌组奖励，奖励先保存再推进剧情；run 牌组限制 12–18 张且
同定义最多三张。角色解锁与剧情回忆按 owner 去重，菲妮在第二章节点加入后可在
后续营地切换，不依赖前端自行声明 `unlocked=true`。

## 运维账号命令

运维命令通过同一 JAR 执行，关闭 Web 启动并从交互终端读取密码，不把密码放入命令行、Shell 历史或日志：

```sh
docker compose run --rm app \
  --spring.main.web-application-type=none \
  --app.admin.command=create \
  --app.admin.username=alice \
  --app.admin.display-name=Alice
```

支持的 `app.admin.command` 为 `create`、`reset-password`、`disable`、`enable`、`list`。数据库连接环境变量必须与应用运行时一致。
密码按 BCrypt 的 72 字节 UTF-8 上限校验，避免多字节密码被静默截断。

## GM 与默认管理员

- `/gm` 使用 ADMIN Cookie 会话和 CSRF；普通用户、匿名请求以及伪造 Bearer
  头不能取得管理权限。
- 开发启动入口通过一次性初始化回执创建默认管理员。重启不会重置密码或重新提权；
  同名普通账号不会被自动提升。
- 首次登录必须改密。管理员可创建普通/测试账号、授予或撤销角色、启停账号并查看
  脱敏审计；测试账号不能持有 ADMIN 角色。
- 已实现账号管理、审计基础和本人隔离测试沙盒；GM 内容草稿预览、发布与归档仍按
  [开发路线](../docs/development/roadmap.md) 在后续任务实现。


## 相关规范

- [文档导航](../docs/README.md)
- [学习与数据一致性](../docs/architecture/learning.md)
- [API 契约](../docs/api/openapi.yaml)
- [数据迁移与恢复](../docs/operations/data-migration.md)
- [开发规范](../docs/development/README.md)
