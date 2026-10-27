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

## 7. 优化

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

## 8. 错误码

| HTTP | code | 说明 |
| --- | --- | --- |
| 400 | `BAD_REQUEST` / `VALIDATION_ERROR` | 参数/计划解析失败 |
| 401 | `UNAUTHORIZED` | 缺少/错误 API Token |
| 403 | `SQL_NOT_ALLOWED` | 非只读、多语句、危险关键字 |
| 404 | `NOT_FOUND` | 数据源/历史/知识条目不存在 |
| 502 | `UPSTREAM_ERROR` | 上游/数据库错误（部分场景返回 200 + status=ERROR） |
| 500 | `INTERNAL_ERROR` | 未预期异常 |

## 9. curl 速查

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
```
