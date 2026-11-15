# FSRS-7 兼容性与调度边界

状态：P0 兼容验证和 P2 worker 已实现，2026-11-15。

## 当前锁定基线

- Web 现有调度器锁定 `ts-fsrs@6.0.0`，模型为 `fsrs-7`。
- 当前 FSRS-7 权重有 34 项，`desiredRetention=0.9`、`enableFuzz=false`、`enableShortTerm=true`、学习步骤 `1m/10m`、重学步骤 `10m`、毕业间隔 1 天。
- 卡片、日志和配置使用 UTC 毫秒存储，并在调度前转换为 `Date`。
- 可重复生成的技术对照位于 `server/src/test/resources/fsrs/fsrs7-golden.json`，生成入口为 `web/scripts/generate-fsrs7-golden.mjs`。

## JVM 实现核查

| 候选 | 结论 | 原因 |
| --- | --- | --- |
| `io.github.open-spaced-repetition:fsrs:0.6.0` | 不直接采用 | 官方 Java-FSRS 文档默认参数为 21 权重，未声明 FSRS-7；不能据名称认定为同模型。 |
| `bee-fsrs` | 不直接采用 | 项目声明为 FSRS-6.x 数学实现，不是当前锁定的 FSRS-7。 |
| `rs-fsrs-java` | 不直接采用 | 公开资料标记为 Scheduler v5 绑定，不满足 FSRS-7 对照要求。 |

本轮没有找到能够通过固定 FSRS-7 序列对照的 JVM 实现，因此 Java 服务不得直接计算或改写正式复习卡片。

## 选定方案

P2 正式复习采用隔离的内部 TS 调度 worker，由 Java 调用：

```text
Web / CLI
  -> Java API：认证、归属、会话、幂等、版本和事务
  -> 内部 scheduler worker：纯 FSRS-7 计算
  -> Java API：同一事务写入 reviewStates + reviewLogs + reward event
```

worker 约束：

- 独立固定 `node:24.19.0` 镜像，只暴露 Compose 内网端口，不发布宿主端口。
- 无账号、数据库、文件持久化或外部网络；不接收 userId 以外的客户端覆盖字段。
- 请求包含卡片快照、锁定的配置快照、评分和服务端确定的 `reviewedAt`；响应包含 afterCard 与 revlog。
- 连接超时 500 ms、响应超时 1500 ms，不重试非幂等计算；Java 按稳定 submissionId 做业务幂等。
- worker 不可用或响应不合法时，正式复习提交失败关闭，不写状态、日志或奖励；客户端保留未确认答案。
- Java 先完成 submissionId 幂等检查，再调用 worker；worker 成功后在同一数据库事务内写状态、正式日志和奖励。
- worker 版本、ts-fsrs 包版本、FSRS 模型版本和配置哈希必须出现在提交结果与诊断中。
- 预览也走同一 worker，但只读且不写业务表。
- P2 worker 由 `web/worker/server.ts` 构建为单文件 Node 制品，只暴露 Compose 内网
  `/health`、`/v1/config`、`/v1/preview` 和 `/v1/review`。
- worker 不可用时 Java 返回 `FSRS_WORKER_UNAVAILABLE`，正式状态、日志、会话和奖励均不写入；
  基于旧复习版本的后续提交返回 `REVIEW_VERSION_CONFLICT`。

## Golden Fixture 覆盖

- 新卡初始快照。
- 同一卡片 Again/Hard/Good/Easy 四档对照。
- 学习步骤内 Good、跨日 Good、Review 后 Again、重学 Hard、后续 Easy。
- JSON 序列化与恢复后的 Good 结果。
- 每个场景保存 beforeCard、afterCard、revlog、UTC 时间、配置和 34 权重。

执行 `FsrsCompatibilityFixtureTest` 可确认夹具仍为 FSRS-7/34 权重且包含四档预览和五步连续复习。未来替换 worker 或升级模型前，必须先扩展现有对照，再修改正式数据版本。
