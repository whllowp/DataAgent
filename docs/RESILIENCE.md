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

## 5. 运维建议

- 开启 Redis 时使用独立 DB/前缀，避免与其他系统冲突
- 监控 `dataagent.query.duration`、`dataagent.query.executions`、缓存命中率
- 熔断阈值按数据源 SLA 调整
- 慢查询阈值应低于客户端超时
- 对高频大查询优先使用 `fresh=false` 与短 TTL
