# FingerMemory Server

Spring Boot 4.1.2、Java 21、MyBatis、MySQL 8.4 和 Spring Session JDBC 构成的多用户 API。Web 生产构建与 API 在同一镜像和同源端口提供；正式复习调用 Compose 内网的固定版本 FSRS-7 worker。

## 本地开发

1. 启动 MySQL 测试实例：

   ```sh
   docker compose -f compose.test.yaml -f compose.dev.yaml up -d mysql fsrs-worker
   ```

2. 设置测试数据库环境变量后启动服务：

   ```sh
   DB_URL='jdbc:mysql://127.0.0.1:3306/finger_memory_test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC' \
   DB_USERNAME=finger_memory_test \
   DB_PASSWORD=finger_memory_test_only \
   ./mvnw spring-boot:run
   ```

`compose.dev.yaml` 只把测试 MySQL 发布到回环 `127.0.0.1:3306`，不得用于共享或现有数据库。

Web 开发服务器通过 Vite `/api` 代理访问 `http://127.0.0.1:8080`。

worker 与前端的制品检查：

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

## P2 API

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

正式复习以 `Idempotency-Key` 作为 submissionId；先查回执，再校验用户数据、会话和复习状态版本。请求只提交评分和证据，afterCard、reviewedAt 和奖励事件由服务端确定。

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
