# 数据导出 + 长期知识库累积：十轮优化

> 目标：参考 NVIDIA auto-ontology 的“可观测、可复用、持续累积”思路，给 DataAgent 增加**服务端数据导出**与**长期知识库累积**，并用十轮实验室验证热路径的冷/暖性能。

## 1. 数据导出（服务端）

只读 SQL 的结果导出为可下载文件，复用查询网关的全部能力（SQL guard、风险拦截、PII 脱敏、超时、熔断、并发配额）。

| 端点 | 说明 |
| --- | --- |
| `POST /api/export` | 执行只读 SQL 并导出（`format` = csv/json/jsonl） |
| `GET /api/export` | 最近导出记录（top 20） |
| `GET /api/export/{id}` | 单条记录 |
| `GET /api/export/{id}/download` | 下载文件（`Content-Disposition: attachment`） |

关键点：

- 导出的行就是查询界面展示的**已脱敏**行——不会比 UI 泄漏更多。
- 格式：CSV（RFC4180，逗号/引号/换行转义，null→空）、JSON（对象数组，字符串转义含 `\u00xx`）、JSONL（每行一个对象）。
- 配置 `dataagent.export.export-dir`（默认 `./data/exports`）、`dataagent.export.max-export-rows`（默认 5000）。
- 记录落库 `data_export`（sql、指纹、格式、文件名、行数、字节数、状态、创建者）。

## 2. 长期知识库累积

把“高频成功 SQL”自动提升为知识条目，并对重复条目去重合并，让知识库随时间自动增长而不是只靠人工沉淀。

| 端点 | 说明 |
| --- | --- |
| `POST /api/knowledge/accumulate` | 手动触发累积（`minFrequency`、`trigger`） |

每日 `@Scheduled` cron（`dataagent.export.accumulation-cron`，默认 `0 15 3 * * *`）自动跑一次 `SCHEDULED` 累积。

累积两步：

1. **提升（promote）**：对出现次数 ≥ `minFrequency` 的成功 SQL 指纹，取最新一条执行记录，若尚未关联则生成 `KnowledgeEntry`（标题取 problem 或 `SQL: ...`，自动带 tags/dialect/耗时/作者/sourceQueryId），并把所有命中记录标记 solved + 关联 knowledgeId。
2. **去重合并（dedup）**：按规范化 SQL（去空白 + 小写）分组，保留 usageCount 最高（其次最新）的条目，把重复条目的 usageCount 合并进幸存者后删除。

结果落库 `knowledge_accumulation_run`（trigger/minFrequency/promoted/merged/deduped/totalKnowledge）。

## 3. 十轮实验室

`GET /api/evolution/export-lab?rounds=10` 用与既有 `/api/evolution/perf` 相同的冷/暖对比方法（冷 = 首次调用，暖 = 连续 N 轮的最后 3 次中位数）测量三个热路径：

| 操作 | 说明 |
| --- | --- |
| `export.csv` | 1000 行 × 8 列合成结果 → CSV 序列化 |
| `export.json` | 同上 → JSON 序列化 |
| `accumulation` | 一次知识库累积 |

返回每项 `coldMs` / `warmMedianMs` / `p95Ms` / `improvementPct` / `roundsMs`。

### 实测结果（本地 H2 demo，`GET /api/evolution/export-lab?rounds=10`）

| 操作 | 冷 (ms) | 暖中位 (ms) | 提升 |
| --- | --- | --- | --- |
| export.csv | 10.4 | 1.5 | 85.6% |
| export.json | 18.9 | 5.0 | 73.7% |
| accumulation | 322.1 | 3.0 | 99.1% |

> 注意：冷样本是首调（含 JVM JIT 编译、类加载、缓存冷启动），暖样本是稳态后的中位数。序列化本身是纯 CPU 热点，提升主要来自 JIT 预热与缓存命中；`accumulation` 冷调用含首次 Hibernate/JPA 元数据初始化（322ms），稳态后降至 ~3ms。实际 HTTP 下载耗时由结果集大小主导。

## 4. 边界与说明

- 导出依赖 `JdbcQueryService.execute`，高危 SQL 默认仍被风险闸门拦截，`allowRisky=true` 才放行。
- 累积阈值 `minFrequency` 默认 3，避免把偶发一次性 SQL 也沉淀成知识。
- 累积用 `AuthContext.currentOrSystem().tenantId()` 隔离租户；`@Scheduled` 任务以 `system` 用户运行。
- 文件落在 `./data/exports/`（已 gitignore 的 `backend/data/` 之下），不会入库。
