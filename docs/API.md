# DataAgent Cloud API

Base URL：`http://127.0.0.1:8080`  
鉴权：除 `/api/health` 外，`/api/**` 需要：

```http
X-API-Key: dataagent-dev-token
# 或
Authorization: Bearer dataagent-dev-token
```

可选审计用户：

```http
X-User-Id: zhangsan
```

> Swagger UI：`/swagger-ui.html`；OpenAPI JSON：`/v3/api-docs`。

## 1. 健康检查

### `GET /api/health`

无需鉴权。

```json
{
  "status": "UP",
  "service": "dataagent-cloud",
  "version": "0.1.0",
  "time": "2026-10-25T17:35:44Z"
}
```

## 2. 数据源

### `GET /api/datasources`

```json
{
  "items": [
    {
      "id": "a97c89b4-db51-442c-b127-37230a2f9367",
      "name": "Demo 销售库",
      "dialect": "H2",
      "jdbcUrl": "jdbc:h2:mem:dataagent_demo;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
      "username": "sa",
      "readOnly": true,
      "maxRows": 500,
      "queryTimeoutSeconds": 15,
      "description": "内置演示库",
      "passwordSet": false,
      "createdAt": "2026-10-25T17:35:21Z"
    }
  ]
}
```

### `POST /api/datasources`

```json
{
  "name": "订单生产库（只读）",
  "dialect": "POSTGRESQL",
  "jdbcUrl": "jdbc:postgresql://db.internal:5432/orders",
  "username": "readonly",
  "password": "******",
  "readOnly": true,
  "maxRows": 500,
  "queryTimeoutSeconds": 15,
  "description": "订单域只读"
}
```

- `dialect`：`POSTGRESQL` / `MYSQL` / `H2`（可省略，由 URL 推断）。
- 允许的 URL 前缀：`jdbc:postgresql:`、`jdbc:mysql:`、`jdbc:mariadb:`、`jdbc:h2:`。
- 密码加密后存储，响应不返回密码。

### `GET /api/datasources/{id}`

### `PUT /api/datasources/{id}`

请求体同创建；`password` 为空表示不修改密码。

### `DELETE /api/datasources/{id}`

`204 No Content`。

### `POST /api/datasources/{id}/test`

```json
{
  "ok": true,
  "latencyMs": 12,
  "message": "连接成功",
  "databaseProduct": "H2",
  "databaseVersion": "2.3.232"
}
```

## 3. 元数据

### `GET /api/datasources/{id}/schemas`

```json
{ "schemas": ["PUBLIC"] }
```

### `GET /api/datasources/{id}/tables?schema=PUBLIC&q=order`

```json
{
  "tables": [
    { "schema": "PUBLIC", "name": "SALES_ORDERS", "type": "BASE TABLE", "remarks": null }
  ]
}
```

### `GET /api/datasources/{id}/tables/{schema}/{table}`

```json
{
  "schema": "PUBLIC",
  "name": "SALES_ORDERS",
  "type": "BASE TABLE",
  "remarks": null,
  "columns": [
    {
      "name": "ID",
      "typeName": "BIGINT",
      "jdbcType": -5,
      "ordinal": 1,
      "nullable": false,
      "primaryKey": true,
      "defaultValue": null,
      "remarks": null
    }
  ],
  "indexes": [
    { "name": "IDX_SALES_DATE", "unique": false, "columns": ["ORDER_DATE"] }
  ]
}
```

## 4. SQL 执行

### `POST /api/sql/execute`

```json
{
  "datasourceId": "a97c89b4-db51-442c-b127-37230a2f9367",
  "sql": "SELECT * FROM sales_orders WHERE status = 'PAID' LIMIT 20",
  "maxRows": 20,
  "timeoutSeconds": 10,
  "includePlan": true,
  "problem": "订单列表慢",
  "tags": "order,list"
}
```

响应：

```json
{
  "queryId": "49a5267f-cf80-4ff2-9d97-d28d488844eb",
  "status": "SUCCESS",
  "columns": [
    { "name": "ID", "label": "ID", "typeName": "BIGINT", "javaType": "java.lang.Long", "jdbcType": -5 }
  ],
  "rows": [[1, 1001, 3, "2025-03-01", 2, 399.00, "PAID", "APP"]],
  "rowCount": 1,
  "truncated": false,
  "elapsedMs": 12,
  "fetchMs": 1,
  "plan": {
    "format": "TEXT",
    "raw": "/* PUBLIC.SALES_ORDERS.indexScan ... */",
    "summary": "H2 plan | SALES_ORDERS",
    "insights": [
      { "level": "INFO", "code": "PLAN_OK", "title": "未发现明显计划告警", "detail": "..." }
    ],
    "explainMs": 1
  },
  "message": null,
  "executedAt": "2026-10-25T17:35:48Z"
}
```

- `status`：`SUCCESS` / `ERROR`；数据库错误也返回 200 + `ERROR`，并写入历史。
- 只读校验失败返回 `403 SQL_NOT_ALLOWED`。
- 超过 `maxRows` 时 `truncated=true`。
- `includePlan` 默认 `false`；Studio 的“运行”会显式传 `true`，云端慢查询（默认 >=500ms）也会自动补计划。执行计划单独调用更清晰。

### `POST /api/sql/explain`

```json
{
  "datasourceId": "...",
  "sql": "SELECT * FROM sales_orders WHERE status = 'PAID'"
}
```

响应：

```json
{
  "queryId": "...",
  "plan": {
    "format": "POSTGRES_JSON",
    "raw": "[{\"Plan\":{...}}]",
    "summary": "Root: Seq Scan on sales_orders | Total Cost: 1500 | Estimated Rows: 12000",
    "insights": [
      { "level": "HIGH", "code": "PG_SEQ_SCAN", "title": "全表扫描", "detail": "..." }
    ],
    "explainMs": 3
  }
}
```

### `POST /api/sql/validate`

```json
{ "datasourceId": "...", "sql": "select 1" }
```

```json
{ "valid": true, "message": "SQL 通过只读校验", "statementType": "SELECT" }
```

## 5. 历史与统计

### `GET /api/history?datasourceId=&solved=&q=&page=0&size=20`

```json
{
  "items": [
    {
      "id": "...",
      "datasourceId": "...",
      "datasourceName": "Demo 销售库",
      "dialect": "H2",
      "userId": "demo",
      "kind": "QUERY",
      "sqlText": "SELECT ...",
      "status": "SUCCESS",
      "durationMs": 1860,
      "fetchMs": 22,
      "rowCount": 8,
      "truncated": false,
      "errorMessage": null,
      "planRaw": null,
      "planFormat": null,
      "problem": "大促期间 GMV 变慢",
      "tags": "慢查询,聚合",
      "solved": true,
      "knowledgeId": "...",
      "createdAt": "2026-10-25T15:35:00Z"
    }
  ],
  "total": 1,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```

列表不返回计划原文；单条详情返回。

### `GET /api/history/{id}`

```json
{
  "execution": { "...": "同上，planRaw/planFormat 有值" },
  "knowledge": [ { "...": "关联知识条目" } ]
}
```

### `PATCH /api/history/{id}/solve`

```json
{
  "title": "按城市统计 GMV：YEAR(order_date) 导致索引失效",
  "problem": "大促期间按城市统计 GMV 变慢",
  "rootCause": "WHERE 对索引列使用 YEAR()，无法范围扫描",
  "optimizationSteps": "1. 改为半开区间\n2. 确认 order_date 索引\n3. EXPLAIN 对比",
  "optimizedSql": "SELECT ... WHERE order_date >= '2025-01-01' AND order_date < '2026-01-01'",
  "tags": "慢查询,聚合,索引失效",
  "afterDurationMs": 380
}
```

响应：

```json
{
  "queryId": "...",
  "knowledgeId": "...",
  "knowledge": { "...": "KnowledgeView" }
}
```

### `PATCH /api/history/{id}/note`

```json
{ "problem": "问题描述", "tags": "tag1,tag2" }
```

### `GET /api/stats/overview`

```json
{
  "totalQueries": 12,
  "queriesToday": 9,
  "averageDurationMs": 555.5,
  "p95DurationMs": 1860,
  "slowQueries": 1,
  "solvedQueries": 2,
  "knowledgeCount": 3,
  "datasourceCount": 1,
  "recentQueries": [],
  "slowestQueries": []
}
```

## 6. 知识库

### `GET /api/knowledge?q=&dialect=&tag=&page=0&size=20`

```json
{
  "items": [
    {
      "id": "...",
      "title": "按城市统计 GMV：YEAR(order_date) 导致索引失效",
      "problem": "...",
      "sqlText": "...",
      "optimizedSql": "...",
      "dialect": "H2",
      "tags": "慢查询,聚合,索引失效",
      "planSummary": "...",
      "rootCause": "...",
      "optimizationSteps": "...",
      "beforeDurationMs": 1860,
      "afterDurationMs": 380,
      "author": "demo",
      "sourceQueryId": "...",
      "usageCount": 3,
      "createdAt": "...",
      "updatedAt": "..."
    }
  ],
  "total": 1,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```

### `GET /api/knowledge/{id}`

### `POST /api/knowledge`

```json
{
  "title": "标题",
  "problem": "问题",
  "sqlText": "SELECT ...",
  "optimizedSql": "SELECT ...",
  "dialect": "POSTGRESQL",
  "tags": "tag1,tag2",
  "planSummary": "...",
  "rootCause": "...",
  "optimizationSteps": "...",
  "beforeDurationMs": 1200,
  "afterDurationMs": 200,
  "sourceQueryId": "..."
}
```

### `PUT /api/knowledge/{id}`

同创建。

### `POST /api/knowledge/{id}/use`

引用次数 +1，返回最新条目。

### `DELETE /api/knowledge/{id}`

`204 No Content`。

## 7. SQL 资产缓存

### `GET /api/sql-assets?q=&domain=&datasourceId=&page=0&size=20`

```json
{
  "items": [
    {
      "id": "...",
      "sqlText": "SELECT city, SUM(amount) FROM sales_orders ...",
      "businessProblem": "按城市分析大促 GMV，定位下滑来源",
      "domain": "销售",
      "tags": "GMV,城市",
      "datasourceId": "...",
      "author": "demo",
      "source": "EXECUTION",
      "executionCount": 3,
      "successCount": 3,
      "failureCount": 0,
      "avgDurationMs": 182,
      "lastDurationMs": 140,
      "knowledgeId": null,
      "firstSeen": "...",
      "lastSeen": "...",
      "semanticSyncedAt": "...",
      "createdAt": "...",
      "updatedAt": "..."
    }
  ],
  "total": 1,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```

### `GET /api/sql-assets/stats`

```json
{ "totalAssets": 8, "withBusinessProblem": 6, "withKnowledge": 2, "linkedToSemantic": 8 }
```

### `POST /api/sql-assets`

```json
{
  "sql": "SELECT ...",
  "businessProblem": "这条 SQL 要回答的业务问题",
  "domain": "销售",
  "tags": "GMV,渠道",
  "datasourceId": "..."
}
```

只缓存文本，不执行。执行过的只读 SQL 会自动 upsert。

### `POST /api/sql-assets/import`

```json
{
  "items": [
    { "sql": "SELECT ...", "businessProblem": "问题 A", "domain": "销售" },
    { "sql": "SELECT ...", "businessProblem": "问题 B", "domain": "财务" }
  ]
}
```

```json
{ "imported": 2 }
```

### `DELETE /api/sql-assets/{id}`

`204 No Content`。

## 8. 语义层

### `GET /api/semantic/overview`

```json
{
  "entities": 3,
  "metrics": 4,
  "terms": 4,
  "links": 29,
  "knowledgeLinked": 3,
  "assetsLinked": 8,
  "lastSync": { "...": "SyncRunView" },
  "recentRuns": []
}
```

### 实体

| Method | Path |
| --- | --- |
| GET | `/api/semantic/entities?q=&domain=` |
| POST | `/api/semantic/entities` |
| PUT | `/api/semantic/entities/{id}` |
| DELETE | `/api/semantic/entities/{id}` |

```json
{
  "name": "sales_order",
  "displayName": "订单",
  "domain": "销售",
  "description": "客户购买商品形成的交易订单",
  "physicalTables": "SALES_ORDERS",
  "synonyms": "订单,交易,order",
  "owner": "data-team",
  "tags": "核心实体,销售",
  "status": "ACTIVE"
}
```

### 指标

| Method | Path |
| --- | --- |
| GET | `/api/semantic/metrics?q=` |
| POST | `/api/semantic/metrics` |
| PUT | `/api/semantic/metrics/{id}` |
| DELETE | `/api/semantic/metrics/{id}` |

```json
{
  "name": "gmv",
  "displayName": "成交总额",
  "entityId": "...",
  "businessDefinition": "支付成功订单金额合计，退款是否抵扣以业务口径为准",
  "expression": "SUM(sales_orders.amount) WHERE status='PAID'",
  "dimensions": "日期,城市,渠道,商品类目",
  "synonyms": "GMV,成交额,销售额,营收",
  "owner": "data-team",
  "tags": "核心指标,销售",
  "status": "ACTIVE"
}
```

### 术语

| Method | Path |
| --- | --- |
| GET | `/api/semantic/terms?q=` |
| POST | `/api/semantic/terms` |
| PUT | `/api/semantic/terms/{id}` |
| DELETE | `/api/semantic/terms/{id}` |

```json
{
  "term": "大促",
  "definition": "促销活动期间，订单、成交与退款口径需要与日常区分",
  "synonyms": "促销,活动,大促",
  "relatedEntityId": "...",
  "relatedMetricId": "...",
  "owner": "data-team",
  "tags": "业务口径,销售",
  "status": "ACTIVE"
}
```

### 实体关系（Ontology）

| Method | Path |
| --- | --- |
| GET | `/api/semantic/relationships?q=` |
| POST | `/api/semantic/relationships` |
| PUT | `/api/semantic/relationships/{id}` |
| DELETE | `/api/semantic/relationships/{id}` |

```json
{
  "name": "sales_order_customer",
  "fromEntityId": "...",
  "toEntityId": "...",
  "relationshipType": "MANY_TO_ONE",
  "joinCondition": "sales_orders.customer_id = customers.id",
  "description": "每笔订单属于一个客户",
  "synonyms": "订单客户,order_customer",
  "owner": "data-team",
  "tags": "核心关系,销售",
  "status": "ACTIVE"
}
```

### `GET /api/semantic/context?q=GMV%20成交额&domain=销售`

返回匹配的实体/指标/术语、实体关系、已验证 Q→SQL，以及通过 AUTO/MANUAL 关联找到的知识条目和 SQL 资产。

### `POST /api/semantic/design`

```json
{
  "idea": "新增按城市和渠道拆解的 GMV 看板，支持按周对比",
  "domain": "销售",
  "datasourceId": "..."
}
```

返回：

```json
{
  "idea": "...",
  "domain": "销售",
  "entities": [],
  "metrics": [],
  "terms": [],
  "designAngles": ["业务目标...", "指标与口径...", "数据实体..."],
  "questions": ["统计粒度和时间范围是什么？"],
  "risks": ["历史 SQL 可能包含旧口径..."],
  "relatedKnowledge": [],
  "relatedAssets": [],
  "promptContext": "你是 DataAgent 的业务语义与数据产品设计助手..."
}
```

### 关联与同步

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/semantic/links?targetType=METRIC&targetId=...` | 语义对象关联的知识/资产 |
| GET | `/api/semantic/links/source?sourceType=KNOWLEDGE&sourceId=...` | 来源关联的语义对象 |
| GET | `/api/semantic/{targetType}/{targetId}/knowledge` | 关联知识 |
| GET | `/api/semantic/{targetType}/{targetId}/assets` | 关联 SQL 资产 |
| POST | `/api/semantic/sync` | 手动同步，返回 SyncRun |
| GET | `/api/semantic/sync/runs` | 最近同步记录 |

`targetType` / `sourceType` 取值：`ENTITY`、`METRIC`、`TERM`；来源：`KNOWLEDGE`、`SQL_ASSET`。

```json
{
  "id": "...",
  "triggerType": "MANUAL",
  "status": "SUCCESS",
  "startedAt": "...",
  "finishedAt": "...",
  "scannedAssets": 8,
  "scannedKnowledge": 3,
  "createdLinks": 29,
  "updatedLinks": 0,
  "removedLinks": 0,
  "message": "语义层与知识库同步完成"
}
```

## 9. 已验证查询（Verified Queries）

人工审核的 Q→SQL，作为语义层的 few-shot 示例和回归基线。

### `GET /api/verified-queries?q=`

```json
[
  {
    "id": "...",
    "question": "按城市统计大促 GMV",
    "sqlText": "SELECT c.city, SUM(o.amount) AS gmv ...",
    "datasourceId": "...",
    "metricId": "...",
    "entityId": "...",
    "tags": "GMV,城市,大促",
    "status": "VERIFIED",
    "owner": "demo",
    "notes": "日期使用半开区间",
    "expectedColumns": "CITY,GMV",
    "useCount": 2,
    "lastValidatedAt": "...",
    "lastValidationStatus": "SUCCESS",
    "lastDurationMs": 42,
    "lastError": null,
    "createdAt": "...",
    "updatedAt": "..."
  }
]
```

### `POST /api/verified-queries`

```json
{
  "question": "按城市统计大促 GMV",
  "sqlText": "SELECT ...",
  "datasourceId": "...",
  "metricId": "...",
  "entityId": "...",
  "tags": "GMV,城市",
  "status": "VERIFIED",
  "owner": "data-team",
  "notes": "口径说明"
}
```

### `PUT /api/verified-queries/{id}` / `DELETE /api/verified-queries/{id}`

更新 / 删除。

### `POST /api/verified-queries/{id}/validate`

只读执行 SQL，记录 `lastValidationStatus / lastDurationMs / expectedColumns / lastError`。

```json
{
  "query": { "...": "view" },
  "success": true,
  "rowCount": 12,
  "durationMs": 42,
  "message": null
}
```

### `POST /api/verified-queries/{id}/use`

使用次数 +1，返回最新 view。

## 10. 长期记忆

### `GET /api/memory?q=&scope=&kind=&status=`

返回长期记忆，按 importance/updatedAt 排序。

```json
[
  {
    "id": "...",
    "scope": "DOMAIN",
    "scopeKey": "销售",
    "kind": "DECISION",
    "memoryKey": "aov.denominator",
    "content": "客单价分母使用支付客户数",
    "source": "USER",
    "confidence": 80,
    "importance": 70,
    "status": "ACTIVE",
    "tags": "客单价,客户数",
    "owner": "demo",
    "useCount": 0,
    "reinforcementCount": 1,
    "knowledgeId": null,
    "createdAt": "...",
    "updatedAt": "..."
  }
]
```

### `POST /api/memory`

```json
{
  "scope": "DOMAIN",
  "scopeKey": "销售",
  "kind": "DECISION",
  "key": "aov.denominator",
  "content": "客单价分母使用支付客户数",
  "source": "USER",
  "confidence": 80,
  "importance": 70,
  "tags": "客单价,客户数",
  "promoteToKnowledge": false,
  "knowledgeTitle": null,
  "sqlText": null
}
```

同一 `scope + scopeKey + key` 会强化已有记忆，而不是重复插入。

`promoteToKnowledge=true` 且提供 `sqlText` 时，同时创建知识库条目：

```json
{
  "memory": { "...": "MemoryView" },
  "knowledge": { "...": "KnowledgeView" }
}
```

### `GET /api/memory/overview`

```json
{
  "total": 5,
  "active": 5,
  "archived": 0,
  "knowledgeLinked": 1,
  "recent": []
}
```

### 其他

| Method | Path | 说明 |
| --- | --- | --- |
| GET | `/api/memory/{id}` | 详情 |
| PUT | `/api/memory/{id}` | 更新内容/置信度/重要性 |
| DELETE | `/api/memory/{id}` | 删除 |
| POST | `/api/memory/{id}/use` | 使用次数 +1 |
| POST | `/api/memory/{id}/archive` | 归档 |
| POST | `/api/memory/archive-expired` | 归档过期记忆 |

### `GET/POST /api/memory/candidates`

对话结束自动抽取的候选只进入审核队列，不直接入库。

```http
GET /api/memory/candidates?status=PENDING
POST /api/memory/candidates/extract
{
  "sessionId": "s-123",
  "domain": "销售",
  "summary": "以后 GMV 默认按支付成功订单统计。"
}
```

审核：

```http
POST /api/memory/candidates/{id}/approve
{
  "force": false,
  "promoteToKnowledge": false,
  "reviewedBy": "alice"
}

POST /api/memory/candidates/{id}/reject
{"reviewedBy": "alice", "reason": "口径不明确"}
```

`approve` 检测到冲突时返回 `approved=false + conflicts`；审核人确认后传 `force=true`。

### `POST /api/feedback`

```json
{
  "sessionId": "s-123",
  "question": "按城市统计 GMV",
  "originalSql": "SELECT city FROM sales_orders",
  "correctedSql": "SELECT city, SUM(amount) FROM sales_orders WHERE status = 'PAID' GROUP BY city",
  "correction": "按城市统计 GMV 时必须过滤 PAID",
  "domain": "销售",
  "rating": 5,
  "reviewer": "alice"
}
```

返回：

```json
{
  "feedback": { "...": "FeedbackView" },
  "memory": { "...": "MemoryView" },
  "verifiedQuery": { "...": "VerifiedQueryView（DRAFT）" },
  "conflicts": []
}
```

## 11. 运维可观测与 SRE 基准

### `GET /api/ops/overview`

```json
{
  "status": "UP",
  "time": "...",
  "uptimeSeconds": 3600,
  "queriesToday": 3,
  "totalQueries": 33,
  "failedQueries": 0,
  "averageDurationMs": 123.5,
  "p95DurationMs": 980,
  "slowQueries": 2,
  "datasourceCount": 1,
  "pools": [
    { "datasourceId": "...", "name": "Demo 销售库", "dialect": "H2", "active": 0, "idle": 1, "total": 1, "waiting": 0 }
  ],
  "heapUsedBytes": 121000000,
  "heapMaxBytes": 8000000000,
  "threadCount": 32,
  "lastSync": { "...": "SyncRunView" },
  "nextScheduledSync": "...",
  "semanticSyncIntervalMs": 1800000,
  "benchmarks": { "total": 3, "active": 3, "failedLastRun": 0, "slowLastRun": 0, "lastRunAt": "..." },
  "nextScheduledBenchmark": "...",
  "benchmarkIntervalMs": 3600000,
  "recentErrors": [],
  "prometheusPath": "/actuator/prometheus"
}
```

`status` 为 `UP` / `DEGRADED`；出现基准失败、语义同步失败或连接池等待时降级。

Prometheus 指标：

- `dataagent_query_duration_seconds{dialect,status}`
- `dataagent_query_executions_total{dialect,status}`
- `dataagent_semantic_sync_duration_seconds{status}`
- `dataagent_benchmark_run_duration_seconds{domain,status}`

### `GET /api/benchmarks`

返回 SRE/运维基准 SQL 用例，含最近状态、耗时、阈值。

### `POST /api/benchmarks`

```json
{
  "name": "SRE 基准：客户表健康计数",
  "domain": "运维基准",
  "description": "验证只读网关、连接池和 customers 表健康状态",
  "sqlText": "SELECT COUNT(*) FROM customers",
  "datasourceId": "...",
  "baselineMs": 200,
  "thresholdMs": 1000,
  "tags": "SRE,availability",
  "status": "ACTIVE"
}
```

### `PUT /api/benchmarks/{id}` / `DELETE /api/benchmarks/{id}`

更新 / 删除基准用例。

### `POST /api/benchmarks/{id}/run`

执行单条基准；结果同时进入执行历史和 SQL 资产，供语义同步关联。

### `POST /api/benchmarks/run`

```json
{ "attempted": 3, "succeeded": 3, "failed": 0 }
```

定时执行由 `dataagent.benchmark.sync-*` 控制，默认 1 小时；本地可用 `scripts/verify-benchmark-scheduler.ps1` 验证。

## 12. 优化

### `POST /api/optimize/analyze`

```json
{
  "datasourceId": "...",
  "sql": "SELECT * FROM customers WHERE LOWER(name) LIKE '%abc' ORDER BY created_at",
  "includePlan": true
}
```

响应：

```json
{
  "datasourceId": "...",
  "dialect": "H2",
  "sql": "...",
  "findings": [
    {
      "code": "SELECT_STAR",
      "severity": "MEDIUM",
      "category": "SQL 写法",
      "title": "SELECT * 读取全部列",
      "detail": "...",
      "suggestion": "...",
      "evidence": "SELECT * FROM customers ..."
    }
  ],
  "plan": { "...": "PlanView 或 null" },
  "metrics": null,
  "promptContext": "你是 DataAgent 的资深 SQL 性能优化专家...",
  "guardrails": [
    "只能生成只读 SQL（SELECT / WITH / VALUES），不得生成 DDL/DML。",
    "..."
  ]
}
```

### `POST /api/optimize/compare`

```json
{
  "datasourceId": "...",
  "originalSql": "SELECT * FROM customers WHERE LOWER(name) LIKE '%abc'",
  "candidateSql": "SELECT id, name FROM customers WHERE name LIKE 'abc%' LIMIT 100",
  "runs": 2,
  "execute": true,
  "notes": "验证前缀匹配"
}
```

响应：

```json
{
  "experimentId": "...",
  "datasourceId": "...",
  "originalPlan": { "...": "PlanView" },
  "candidatePlan": { "...": "PlanView" },
  "originalMs": 3,
  "candidateMs": 1,
  "improvementPercent": 66.67,
  "verdict": "FASTER",
  "originalFindings": [],
  "candidateFindings": [],
  "notes": "验证前缀匹配",
  "warnings": [
    "基准只读并限制返回行数，生产前请使用真实参数与数据分布复测。",
    "耗时取 2 次成功执行的最小值，未清理数据库缓存，也未模拟并发。",
    "必须确认改写后的结果集与原 SQL 完全一致：行数、列、排序、去重与 NULL 语义。"
  ],
  "createdAt": "..."
}
```

`verdict`：`FASTER` / `SLOWER` / `SIMILAR` / `ORIGINAL_FAILED` / `CANDIDATE_FAILED` / `PLAN_ONLY`。

### `POST /api/optimize/prompt`

```json
{ "datasourceId": "...", "sql": "SELECT ...", "includePlan": true }
```

```json
{ "prompt": "...", "guardrails": ["..."] }
```

## 13. 错误码

| HTTP | code | 说明 |
| --- | --- | --- |
| 400 | `BAD_REQUEST` / `VALIDATION_ERROR` | 参数/计划解析失败 |
| 401 | `UNAUTHORIZED` | 缺少/错误 API Token |
| 403 | `SQL_NOT_ALLOWED` | 非只读、多语句、危险关键字 |
| 404 | `NOT_FOUND` | 数据源/历史/知识条目不存在 |
| 502 | `UPSTREAM_ERROR` | 上游/数据库错误（部分场景返回 200 + status=ERROR） |
| 500 | `INTERNAL_ERROR` | 未预期异常 |

## 14. 审计、缓存与血缘

### `GET /api/audit`

```http
GET /api/audit?actor=alice&action=SQL_EXECUTE&status=ERROR&limit=100&offset=0
```

返回不可变审计事件：actor、action、resource、datasourceId、status、detail、clientIp、createdAt。

### `GET /api/audit/overview`

```json
{
  "last24h": 120,
  "failed24h": 3,
  "blocked24h": 2,
  "topActions": [
    {"action": "SQL_EXECUTE", "count": 96}
  ]
}
```

### `GET /api/ops/cache`

```json
{
  "hits": 42,
  "misses": 8,
  "puts": 8,
  "evictions": 0,
  "size": 8,
  "maxEntries": 200,
  "ttlSeconds": 30
}
```

`POST /api/ops/cache/clear` 清空缓存并记录审计。

### `GET /api/lineage/tables`

返回 SQL 资产、Verified Query、语义实体涉及的表汇总。

### `GET /api/lineage/impact?table=SALES_ORDERS`

返回：

- `assets`：引用该表的 SQL 资产
- `verifiedQueries`：引用该表的已验证查询
- `semanticEntities`：`physicalTables` 包含该表的实体

### `GET /api/lineage/fields`

返回字段级引用：`table`、`column`、`piiType`、资产/Verified Query 计数。

### `GET /api/lineage/field-impact?table=SALES_ORDERS&column=AMOUNT`

返回引用该字段的 SQL 资产和 Verified Query。

### `POST /api/audit/export`

```json
{
  "from": "2026-11-01T00:00:00Z",
  "to": "2026-11-08T00:00:00Z",
  "destination": "FILE"
}
```

`destination`：`FILE` / `SIEM` / `OBJECT_STORE`。返回文件名、行数、字节数、SHA-256、状态。

`GET /api/audit/export/download?from=&to=` 直接下载 JSONL。`POST /api/audit/purge` 按保留策略清理。

## 15. 安全、PII 与韧性

### `GET /api/auth/me`

```json
{"userId":"key:bi-reader","tenantId":"default","role":"READ","apiKeyId":"...","datasourceIds":["..."]}
```

### `GET/POST /api/api-keys`

```json
{
  "name": "bi-reader",
  "role": "READ",
  "tenantId": "default",
  "datasourceIds": ["<uuid>"],
  "expiresAt": null
}
```

返回一次性 `key`（`da_...`）；服务端只保存 hash。`DELETE /api/api-keys/{id}` 撤销。

### `GET /api/pii/classify?column=customer_email`

```json
{"column":"customer_email","type":"EMAIL","masked":true,"maskExample":"e***@example.com"}
```

### `GET /api/ops/resilience`

返回数据源失败计数、熔断截止时间、慢查询限流截止时间。

## 16. ETL / DolphinScheduler

### `POST /api/etl/sync/dolphinscheduler`

导入 DolphinScheduler process definitions、task relations、schedule 和 workflow dependencies。ADMIN only。

### `POST /api/etl/sync/dolphinscheduler/pull?projectCode=123`

从 DolphinScheduler REST 拉取并导入（配置 `dataagent.dolphinscheduler.*`）。

### `GET /api/etl/overview`

workflow/task 数、已排程、依赖环、孤立任务。

### `GET /api/etl/workflows`

返回调度 cron、任务数、上下游 workflow 数、下次运行时间。

### `GET /api/etl/workflows/{id}/graph`

任务节点、依赖边、拓扑顺序、环、孤立任务、源/目标表。

### `GET /api/etl/workflows/{id}/schedule`

cron、timezone、nextRun、关键路径、拓扑顺序。

### `GET /api/etl/workflows/{id}/analysis`

端到端分析：关键路径、源/目标表、孤立任务、上下游 workflow 链。

### `GET /api/etl/lineage?table=ODS_ORDERS&direction=downstream&depth=10`

追踪 ETL 任务链中的表级上下游：

- 节点：表 + depth
- 边：fromTable、toTable、task、workflow、schedule
- 相关 workflow 列表

### `GET /api/etl/tasks/{taskId}/fields`

返回该 ETL 任务的字段映射。

### `GET /api/etl/field-lineage?table=ODS_ORDERS&column=AMOUNT`

返回源字段 → 目标字段的 ETL/OpenLineage 字段边。

### `POST /api/etl/lineage/rebuild`

重建当前租户的 ETL table/field lineage（ADMIN）。

### OpenLineage / Marquez

```http
POST /api/v1/lineage
POST /api/openlineage/events
GET  /api/openlineage/events?jobName=etl_job
GET  /api/openlineage/jobs
GET  /api/v1/namespaces
GET  /api/v1/namespaces/{namespace}/jobs
GET  /api/v1/namespaces/{namespace}/jobs/{job}
GET  /api/v1/namespaces/{namespace}/jobs/{job}/runs
GET  /api/v1/namespaces/{namespace}/jobs/{job}/runs/{runId}
```

接受标准 OpenLineage RunEvent，自动维护：

- `lineage_edge`（表级）
- `lineage_field_edge`（columnLineage）
- `/api/lineage/impact` 中的 `openLineageJobs`
- `/api/lineage/field-impact` 中的 `fieldEdges`

`.../runs/{runId}` 返回该运行实例的 tableEdges / fieldEdges，即运行实例级血缘。

### `GET /api/lineage/field-impact?table=&column=`

现在返回：

- SQL 资产
- Verified Query
- `fieldEdges`：ETL/OpenLineage 字段映射

## 17. curl 速查

```bash
TOKEN=dataagent-dev-token
BASE=http://127.0.0.1:8080

curl -s $BASE/api/datasources -H "X-API-Key: $TOKEN"

curl -s "$BASE/api/datasources/<id>/tables?schema=PUBLIC" -H "X-API-Key: $TOKEN"

curl -s -X POST "$BASE/api/sql/execute" \
  -H "X-API-Key: $TOKEN" -H "Content-Type: application/json" \
  -d '{"datasourceId":"<id>","sql":"SELECT * FROM customers LIMIT 5","maxRows":5}'

curl -s -X POST "$BASE/api/optimize/analyze" \
  -H "X-API-Key: $TOKEN" -H "Content-Type: application/json" \
  -d '{"datasourceId":"<id>","sql":"SELECT * FROM customers WHERE LOWER(name) LIKE '\''%a%'\''"}'

curl -s "$BASE/api/knowledge?q=索引失效" -H "X-API-Key: $TOKEN"

curl -s "$BASE/api/sql-assets?q=GMV&size=5" -H "X-API-Key: $TOKEN"

curl -s "$BASE/api/semantic/context?q=GMV%20城市" -H "X-API-Key: $TOKEN"

curl -s -X POST "$BASE/api/semantic/design" \
  -H "X-API-Key: $TOKEN" -H "Content-Type: application/json" \
  -d '{"idea":"新增按城市和渠道拆解的 GMV 看板","domain":"销售"}'

curl -s -X POST "$BASE/api/semantic/sync" -H "X-API-Key: $TOKEN"

curl -s "$BASE/api/ops/overview" -H "X-API-Key: $TOKEN"

curl -s "$BASE/api/benchmarks" -H "X-API-Key: $TOKEN"

curl -s -X POST "$BASE/api/benchmarks/run" -H "X-API-Key: $TOKEN"

curl -s "$BASE/actuator/prometheus" | grep dataagent_

curl -s "$BASE/api/semantic/relationships?q=订单" -H "X-API-Key: $TOKEN"

curl -s "$BASE/api/verified-queries?q=GMV" -H "X-API-Key: $TOKEN"

curl -s -X POST "$BASE/api/verified-queries/<id>/validate" -H "X-API-Key: $TOKEN"

curl -s "$BASE/api/memory?q=GMV" -H "X-API-Key: $TOKEN"

curl -s -X POST "$BASE/api/memory" \
  -H "X-API-Key: $TOKEN" -H "Content-Type: application/json" \
  -d '{"scope":"GLOBAL","kind":"PREFERENCE","key":"gmv.definition","content":"GMV 按支付成功订单统计"}'

curl -s "$BASE/api/audit/overview" -H "X-API-Key: $TOKEN"

curl -s "$BASE/api/ops/cache" -H "X-API-Key: $TOKEN"

curl -s "$BASE/api/lineage/impact?table=SALES_ORDERS" -H "X-API-Key: $TOKEN"

curl -s "$BASE/api/lineage/fields" -H "X-API-Key: $TOKEN"

curl -s "$BASE/api/pii/classify?column=customer_email" -H "X-API-Key: $TOKEN"

curl -s "$BASE/api/ops/resilience" -H "X-API-Key: $TOKEN"

curl -s -X POST "$BASE/api/audit/export" \
  -H "X-API-Key: $TOKEN" -H "Content-Type: application/json" \
  -d '{"destination":"FILE"}'

curl -s "$BASE/api/etl/overview" -H "X-API-Key: $TOKEN"

curl -s "$BASE/api/etl/workflows" -H "X-API-Key: $TOKEN"

curl -s "$BASE/api/etl/lineage?table=ODS_ORDERS&direction=downstream" -H "X-API-Key: $TOKEN"

curl -s "$BASE/api/etl/field-lineage?table=ODS_ORDERS&column=AMOUNT" -H "X-API-Key: $TOKEN"

curl -s "$BASE/api/openlineage/jobs" -H "X-API-Key: $TOKEN"
```
