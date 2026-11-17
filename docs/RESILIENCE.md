# 韧性与 Redis

## 1. 数据源熔断

`DatasourceResilience` 按数据源维护：

- 连续失败计数
- 熔断打开时间
- 慢查询限流时间
- 最近耗时

默认：

```yaml
dataagent:
  resilience:
    failure-threshold: 5
    open-ms: 30000
    slow-threshold-ms: 5000
    slow-cooldown-ms: 60000
```

行为：

- 连续失败达到阈值 → `503 SERVICE_UNAVAILABLE`，熔断 30s
- 成功查询 reset 失败计数
- 单次查询 >= 5s → 该数据源 60s 内返回 `429 TOO_MANY_REQUESTS`
- `GET /api/ops/resilience` 查看状态

## 2. 瞬时错误重试

对 SQLState：

- `08*`（连接异常）
- `40001` / `40P01`（序列化/死锁）
- `57P01`（管理员断开）

自动重试一次，且：

- 只重试只读查询
- 第二次使用 `fresh=true` 绕过缓存
- 审计记录 `SQL_RETRY`
- 避免无限重试

## 3. Redis 分布式缓存与配额

默认关闭，回退本地：

```yaml
dataagent:
  redis:
    enabled: false
    key-prefix: dataagent
    quota-ttl-seconds: 120

spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
```

开启后：

- 查询结果缓存写入 Redis（TTL 与本地一致）
- 本地缓存仍作为一级缓存
- 并发配额使用 Redis `INCR + EXPIRE`：
  - `dataagent:quota:global`
  - `dataagent:quota:ds:<datasourceId>`
- 释放时 `DECR`
- Redis 不可用自动回退本地 Semaphore
- 健康检查 `management.health.redis.enabled` 与开关一致，避免未启用时健康 DOWN

注意：Redis 配额是**软配额**，进程崩溃可能残留计数，依赖 TTL 自动回收。

## 4. 与缓存/并发的关系

- 缓存命中不占用数据库连接和并发配额
- 熔断/慢查询限流在缓存 miss 后触发
- 并发配额在熔断检查后获取
- 查询结束释放配额；SQL 异常也释放

## 5. 并发排队调度

`QueryConcurrencyLimiter` 在并发耗尽时不再立即拒绝，而是进入 FIFO 公平队列等待：

```yaml
dataagent:
  query:
    queue-wait-timeout-ms: 30000   # 排队等待上限，超时返回 429
    max-queue-depth: 200           # 队列长度上限，满则立即 429
```

- 全局信号量（默认 16）+ 每数据源信号量（默认 4）均为公平模式
- 排队等待超时 / 队列已满 / 线程中断 → `429 TOO_MANY_REQUESTS`
- `GET /api/ops/queue` 查看：活跃数、排队数、最大深度、等待超时、累计拒绝数、平均等待耗时
- 指标 `totalRejected` / `totalQueued` / `totalWaitedNanos` 记录在限流器内

## 6. SQL 执行超时与自动杀会话

查询执行时在数据库侧登记会话，超时由看门狗主动终止（不依赖 JDBC 驱动放弃）：

- `QueryKiller` 按方言终止：PostgreSQL `pg_terminate_backend`、MySQL `KILL`、H2 `CANCEL_SESSION`
- `SessionWatchdog` 每 5s 扫描一次，kill 超过 deadline 的会话
- `GET /api/ops/sessions` 列出在途会话；`POST /api/ops/sessions/{queryId}/kill` 手动终止
- `statement.setQueryTimeout(timeout)` 仍作为第一道防线，看门狗是兜底

```yaml
dataagent:
  query:
    kill-enabled: true            # 关闭后看门狗不主动杀会话
    session-watchdog-ms: 5000     # 扫描间隔
```

## 7. 高危 SQL 拦截

`SqlRiskAssessor` 在执行前对只读 SQL 评分，分数达到阈值直接拦截：

```yaml
dataagent:
  query:
    risk-block-score: 70          # >= 该分数拦截执行
    risk-warn-score: 40           # >= 该分数标记 MEDIUM
```

- 加权规则：全表扫描 30、笛卡尔/逗号连接 30、前置通配符 20、函数列 15、`SELECT *` 15、`ORDER BY` 无 `LIMIT` 10、`OR` 10、`NOT IN` 10、大 `OFFSET`(≥1万) 10、`DISTINCT` 5、`UNION` 5、`IN` 子查询 5、隐式转换 5（上限 100）
- 级别：`>=80` CRITICAL、`>=60` HIGH、`>=40` MEDIUM、`>0` LOW、`0` NONE
- 被拦截返回 `403`（code `SQL_NOT_ALLOWED`），审计 `SQL_RISK_BLOCKED`
- 显式 `allowRisky=true` 可覆盖（供基准/优化实验室等受控场景使用）
- `POST /api/optimize/risk` 预检评分；`POST /api/optimize/rewrite` 安全改写；`POST /api/optimize/evolve` 十轮优化演进

## 8. 运维建议

- 开启 Redis 时使用独立 DB/前缀，避免与其他系统冲突
- 监控 `dataagent.query.duration`、`dataagent.query.executions`、缓存命中率
- 熔断阈值按数据源 SLA 调整
- 慢查询阈值应低于客户端超时
- 对高频大查询优先使用 `fresh=false` 与短 TTL
