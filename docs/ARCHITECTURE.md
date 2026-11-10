# DataAgent 架构设计

> 版本：0.1（MVP）  
> 目标：把 SQL 执行、计划、耗时和团队经验放到云端，把编辑器和 DSH 大模型留在端侧。

## 1. 目标与非目标

### 1.1 目标

1. 端侧不再维护重型数据库客户端和连接池，降低内存占用。
2. 所有执行可审计：用户、数据源、SQL、耗时、取数耗时、行数、错误、执行计划。
3. SQL 与排查经验可团队共享：从执行历史一键沉淀为知识条目。
4. SQL 优化不是“让模型猜”，而是让模型看到真实执行计划、真实耗时，并能反复调用工具验证。
5. 数据库凭据不落到端侧；端侧只持有可撤销的 API Token。

### 1.2 非目标（当前版本）

- 不替代完整 DataGrip：不做事务编辑、写入审批、ER 图、SSH 隧道、可视化执行计划、自动补全。
- 不做通用 BI：结果集不持久化，只回传当前查询结果。
- 不做完整 SQL 解析器：SQL 守卫是保守的只读网关，不是数据库权限系统。
- 不做多租户计费/配额；RBAC 与 SSO 放在下一阶段。

## 2. 总体架构

```mermaid
flowchart TB
  subgraph Edge["端侧（DSH Desktop / Web）"]
    direction TB
    Panel["DataAgent SQL 面板<br/>Schema 树 / 编辑器 / 结果 / 历史 / 知识库"]
    DSH["DSH Agent + 大模型"]
    MCP["MCP stdio bridge<br/>44 个 dataagent_* 工具"]
    Panel -->|"HTTPS / REST"| Gateway
    DSH -->|"tool call"| MCP
    MCP -->|"HTTPS / REST"| Gateway
  end

  subgraph Cloud["云上（Spring Boot）"]
    direction TB
    Gateway["API Gateway<br/>Token / CORS / 错误模型"]
    DS["DatasourceService<br/>加密凭据 + HikariCP 池"]
    Meta["MetadataService<br/>DatabaseMetaData"]
    Guard["SqlGuard<br/>只读 / 单语句 / 超时 / 行数"]
    Exec["JdbcQueryService<br/>执行 + 采集"]
    Plan["PlanService<br/>EXPLAIN + 解析 + 洞察"]
    Hist["HistoryService<br/>历史 / 统计 / 沉淀"]
    KB["KnowledgeService<br/>检索 / 引用"]
    Opt["OptimizationService<br/>规则 + Prompt + 基准"]
    Store[("元数据库<br/>H2 / PostgreSQL")]
    Gateway --> DS
    Gateway --> Meta
    Gateway --> Guard
    Gateway --> Exec
    Gateway --> Hist
    Gateway --> KB
    Gateway --> Opt
    Exec --> Guard
    Exec --> DS
    Exec --> Plan
    Exec --> Store
    Plan --> Store
    Hist --> Store
    KB --> Store
    Opt --> Exec
    Opt --> Plan
  end

  DB[("业务库<br/>PostgreSQL / openGauss / DWS / MySQL / H2")]
  Exec -->|"JDBC（只读）"| DB
  Meta -->|"JDBC metadata"| DB
  DS -->|"按需建池"| DB
```

### 2.1 端云职责边界

| 能力 | 端侧 | 云侧 |
| --- | --- | --- |
| SQL 编辑、页签、对象浏览 | ✅ | 提供元数据 API |
| 数据库连接/密码 | ❌ | ✅ 加密存储 + 连接池 |
| SQL 执行 | ❌ | ✅ 只读网关、超时、行数限制 |
| 执行计划 | 展示 | ✅ 采集、解析、洞察 |
| 执行历史 | 展示/检索 | ✅ 持久化、统计 |
| 大模型优化 | ✅ DSH LLM | 提供真实计划/基准/知识 |
| 知识库 | 展示/编辑 | ✅ 持久化、检索、权限（后续） |
| 审计 | 只带用户标识 | ✅ 全量记录 |

## 3. 云端模块

### 3.1 API 层

- 所有 `/api/**` 默认要求 `X-API-Key` 或 `Authorization: Bearer`。
- `X-User-Id` 作为审计用户；缺省 `demo`。生产应由网关注入，不能让客户端任意伪造。
- 统一错误体：

```json
{
  "code": "SQL_NOT_ALLOWED",
  "message": "DataAgent 默认只允许只读查询...",
  "path": "/api/sql/execute",
  "timestamp": "2026-10-25T17:00:00Z"
}
```

- CORS 默认允许本地 DSH 端口与 Studio 开发端口；生产通过 `DATAAGENT_CORS_ORIGINS` 收紧。
- OpenAPI：`/v3/api-docs`，Swagger UI：`/swagger-ui.html`。

### 3.2 数据源服务

- 一个数据源对应一个懒加载的 HikariCP 池（最多 5 个连接，探活 1 个）。
- 密码用 AES-GCM 加密，密文存 `datasource.password_cipher`，密钥来自 `DATAAGENT_SECRET_KEY`。
- 支持方言：`POSTGRESQL`（含 openGauss/DWS 的 PostgreSQL 协议）、`MYSQL`、`H2`。
- 连接 URL 白名单：`jdbc:postgresql:`、`jdbc:mysql:`、`jdbc:mariadb:`、`jdbc:h2:`。
- 拒绝 H2 `INIT=` / `RUNSCRIPT`、MySQL 本地文件读取等危险参数。
- 连接创建不做阻塞式强校验；`POST /api/datasources/{id}/test` 显式探活。

> 生产建议：数据源注册只对管理员开放；业务库使用只读账号；通过 VPC/安全组限制云服务出口。当前 MVP 的数据源创建接口没有管理员角色校验，这是已知边界。

### 3.3 元数据服务

- 基于 `DatabaseMetaData`，不依赖数据库特有 catalog 表。
- `schemas`：PostgreSQL/H2 用 schema；MySQL 用 catalog。
- `tables`：表、视图、物化视图；过滤系统 schema。
- `describeTable`：列、类型、可空、默认值、主键、索引。
- 当前不做元数据缓存；高频场景可在下一阶段加 Caffeine + TTL。

### 3.4 SQL 网关

`JdbcQueryService` 的核心约束：

| 约束 | 实现 |
| --- | --- |
| 只读 | `SqlGuard` 首关键字白名单 + 危险关键字扫描；`Connection.setReadOnly` |
| 单语句 | 扫描引号/注释外的分号 |
| 超时 | `Statement.setQueryTimeout`，默认 15s，硬上限 60s |
| 行数 | `Statement.setMaxRows(maxRows + 1)`，默认 500，硬上限 5000 |
| 截断 | 多取 1 行判断是否截断 |
| 事务 | `autoCommit=false` + 最后 rollback，避免驱动隐式提交 |
| 结果大小 | 单单元格字符串默认 20k 字符；二进制 base64 限 64KB |
| 计划 | UI 运行默认采集；慢查询（默认 >=500ms）自动 EXPLAIN；不执行 EXPLAIN ANALYZE |
| 历史 | 成功/失败都写 `query_execution`；计划写入 `plan_raw` |

`SqlGuard` 不是 SQL 解析器：它会把字符串/注释剥离后再做关键字扫描，但仍可能误伤列名、或漏掉非常规语法。**真正的安全边界是数据库只读账号 + 网络 + 审计**。

### 3.5 执行计划服务

按方言生成：

```sql
-- PostgreSQL / openGauss / DWS
EXPLAIN (FORMAT JSON, COSTS TRUE, VERBOSE FALSE) <sql>

-- MySQL
EXPLAIN FORMAT=JSON <sql>

-- H2
EXPLAIN <sql>
```

解析：

- PostgreSQL：`Plan` 树；识别 Seq Scan / Nested Loop / Sort / Hash Join / 高 Total Cost。
- MySQL：`query_block` 树；识别 `access_type=ALL`、无 key、filesort、temporary table。
- H2：文本计划；识别 `tableScan` / FULL SCAN / SORT。
- 原始计划始终保留；解析失败时降级为“保留原文 + 提示无法结构化解析”。

### 3.6 历史与统计

每次执行都写一条 `query_execution`，包括 `kind=QUERY|EXPLAIN`。历史列表默认不返回计划原文，详情才返回，避免列表响应过大。

统计：

- 总查询数、今日查询数
- 平均耗时、P95 耗时（对成功执行排序取分位）
- 慢查询数（`>= 1000ms`）
- 已解决数、知识条目数、数据源数
- 最近查询、最慢查询

### 3.7 知识库

当前是“结构化经验库”，不是文档库：

```
问题/现象 + 原 SQL + 方言 + 标签
+ 执行计划摘要
+ 根因
+ 优化步骤
+ 优化 SQL
+ 优化前/后耗时
+ 作者、来源 queryId、引用次数
```

生成路径：

1. 用户在历史中点击“沉淀”；
2. 填写根因、步骤、优化 SQL、后置耗时；
3. `HistoryService.markSolved` 创建或更新知识条目，并把执行记录标记为 `solved`；
4. 相同 queryId 重复沉淀会更新已有条目，不重复创建。

检索：`title / problem / sqlText / tags` 的 `LIKE` 检索 + 方言/标签过滤。  
下一阶段：pgvector 语义检索、LLM 自动摘要、按业务域/团队分层、去重。

### 3.8 优化服务

`POST /api/optimize/analyze`：

1. `SqlGuard` 校验；
2. 取执行计划（可关）；
3. `SqlAnalyzer` 静态规则：SELECT *、无 WHERE、前置通配符、列上函数、OR、NOT IN、ORDER BY 无 LIMIT、DISTINCT、UNION、隐式 JOIN、IN 子查询、隐式类型转换；
4. 合并计划洞察；
5. 生成 DSH Prompt（含 SQL、计划、发现、约束、JSON 输出 schema）；
6. 返回 findings + plan + prompt。

`POST /api/optimize/compare`：

1. 分别获取原 SQL 与候选 SQL 的计划；
2. 可选真实执行 N 次（默认 2，最多 5），取成功执行的最小耗时；
3. 计算 `improvementPercent = (original - candidate) / original * 100`；
4. verdict：`FASTER / SLOWER / SIMILAR / ORIGINAL_FAILED / CANDIDATE_FAILED / PLAN_ONLY`；
5. 写入 `optimization_experiment`，并附带语义等价性、缓存、单次基准等警告。

> 对比不会自动判断结果集语义是否等价。模型和用户必须自行确认行数、列、排序、去重、NULL 语义。

### 3.9 SQL 资产缓存

`query_execution` 是“每次执行的审计”，`sql_asset` 是“按 SQL 指纹聚合的开发资产”：

- 执行后自动 upsert（`source=EXECUTION`）；
- 手工缓存未执行 SQL（`source=EDITOR`）；
- 标记解决/写知识时回写业务问题和知识 ID（`source=KNOWLEDGE`）；
- 启动时对既有历史做 backfill；
- 指纹归一化空白、大小写、字符串/数字字面量，把同一业务问题的参数化查询聚合。

字段包括：SQL、业务问题、业务域、标签、数据源、作者、执行/成功/失败次数、平均/最近耗时、最近计划、关联知识、最近语义同步时间。

### 3.10 语义层

语义层解决“SQL 历史能查到，但不知道业务口径”的问题：

- **Entity**：客户/商品/订单等业务对象，关联物理表、主键、粒度、同义词；
- **Metric**：GMV/订单量/客单价等指标，包含业务定义、表达式、维度、同义词、负责人；
- **Term**：大促/渠道/有效订单等业务术语，可关联实体与指标；
- **Link**：`KNOWLEDGE/SQL_ASSET → ENTITY/METRIC/TERM` 的关联，带置信度、原因与 `AUTO/MANUAL` 来源。

设计助手 `POST /api/semantic/design`：

1. 对业务想法分词；
2. 匹配实体/指标/术语（指标会自动带出所属实体）；
3. 通过 Link 找到关联知识和历史 SQL；
4. 生成设计角度、待确认问题、风险；
5. 生成供 DSH 输出 PRD/数据模型/API/验收 SQL 的 Prompt。

### 3.11 语义层 ↔ 知识库定期互通

`SemanticSyncService` 由 `SemanticSyncScheduler` 定时执行（默认 30 分钟，启动 2 分钟后首次），也可 `POST /api/semantic/sync` 手动触发。

流程：

1. 扫描全部知识条目和 SQL 资产；
2. 对每个语义对象构造显示名/名称/同义词别名；
3. 在来源文本中做关键词匹配（MVP，后续替换为 embedding/LLM）；
4. 每个来源最多保留 12 个高置信度目标；
5. 重建 `link_source=AUTO` 的关联，保留 MANUAL；
6. 更新 SQL 资产 `semantic_synced_at`；
7. 写 `semantic_sync_run`（扫描数、新增/更新/删除、状态、错误）。

> 详细设计与治理建议见 [SEMANTIC.md](SEMANTIC.md)。  
> 内置演示定义为 `owner=demo`、`status=DRAFT`，正式使用前必须替换为团队确认口径。

### 3.12 运维可观测

`OpsService` / `GET /api/ops/overview` 聚合：

- 查询侧：今日/总查询、失败数、平均/P95、慢查询、最近失败 SQL；
- 连接池：每个数据源的 active/idle/total/waiting；
- JVM：堆使用/最大、线程数、进程 uptime；
- 调度侧：最近语义同步、下一次预计、链路增删数量；
- SRE 基准：总数、ACTIVE、最近失败/慢用例、最近运行时间；
- Prometheus：`/actuator/prometheus`，并记录查询、语义同步、基准执行的 Timer/Counter。

前端 header 的「运维可观测」弹窗每 5s 轮询该接口，状态为 `UP` / `DEGRADED`。

### 3.13 SRE 基准数据集

`sql_benchmark_case` 保存 SRE/运维 SQL 基准用例：

- 字段：名称、业务域、描述、SQL、数据源、baseline/threshold、标签、状态；
- 最近结果：`last_run_at / last_duration_ms / last_status / last_error`；
- 手动 `POST /api/benchmarks/{id}/run` 或 `POST /api/benchmarks/run`；
- `BenchmarkScheduler` 默认每 1 小时执行 ACTIVE 用例，启动 5 分钟后首次；
- 基准执行复用只读 SQL 网关，因此自动写入执行历史和 SQL 资产，下一轮语义同步可关联到 SRE 实体/指标/术语；
- 内置 3 条演示基准（可用性、错误率、P95 延迟），`owner=demo` 的语义定义仍为 DRAFT。

### 3.14 Ontology 实体关系

`semantic_relationship` 把实体间的 join 路径显式化：

- `from_entity_id` / `to_entity_id`
- `relationship_type`：ONE_TO_ONE / ONE_TO_MANY / MANY_TO_ONE / MANY_TO_MANY
- `join_condition`：真实列条件，如 `sales_orders.customer_id = customers.id`
- `description` / `synonyms` / `owner` / `status`

`SemanticService.context` 会返回命中实体的关系；设计助手在 Prompt 中明确 join 路径、基数和一对多放大风险，禁止模型猜 JOIN。

### 3.15 Verified Query 目录

`verified_query` 保存人工审核的 Q→SQL：

- 问题、SQL、数据源、关联指标/实体、标签、owner、notes
- 状态、最近验证状态/耗时/错误、使用次数
- `POST /api/verified-queries/{id}/validate` 复用只读网关执行并记录结果
- 语义上下文与设计助手会带出相关已验证查询，作为 few-shot 示例与回归基线

> 业界参考见 [INDUSTRY-BENCHMARK.md](INDUSTRY-BENCHMARK.md)。

### 3.16 长期记忆

`agent_memory` 保存跨会话的长期记忆：

- 作用域：GLOBAL / DOMAIN / DATASOURCE / USER
- 类型：PREFERENCE / DECISION / FACT / PATTERN / OPEN_QUESTION
- 同一 `scope + scopeKey + key` 强化已有记忆（confidence、reinforcementCount、updatedAt）
- 支持过期时间、归档、使用次数
- `promoteToKnowledge=true` 时创建 `knowledge_entry` 并记录 `knowledge_id`

`MemoryService.context` 会把 GLOBAL、当前业务域和 embedding 召回的记忆注入语义上下文/设计 Prompt；每日 03:15 归档过期记忆。详细设计见 [MEMORY.md](MEMORY.md)。

补充机制：

- **候选审核**：对话结束抽取到 `memory_candidate`，人工 approve/reject 后才入库
- **冲突检测**：同 scope+key 的新旧内容 embedding 相似度低或否定词不一致时提示，审核可强制通过
- **反馈闭环**：`agent_feedback` → 长期记忆 + DRAFT verified query
- **向量召回**：本地 hashed n-gram embedding + cosine，替换纯 tokenize 检索

### 3.17 生产治理层

- **审计**：`audit_event` 追加表，`REQUIRES_NEW` 独立事务；记录 SQL 执行/拦截、数据源变更、记忆审核、反馈
- **查询缓存**：短 TTL 内存缓存，key 含 datasource/sql/maxRows/includePlan；数据源变更失效；`fresh=true` 绕过
- **并发保护**：全局 + 数据源级 Semaphore，超限 429
- **血缘**：从 SQL 资产/Verified Query 提取表引用（识别 CTE），结合 `semantic_entity.physical_tables` 做影响分析

详见 [GOVERNANCE.md](GOVERNANCE.md)。

### 3.18 API Key、租户与 PII

- `api_key`：只保存 keyPrefix + SHA-256；一次性明文返回
- 角色 `READ / EXECUTE / ADMIN`；`api_key_datasource` 精确 ACL
- `datasource.tenant_id`，`DatasourceService` 在 list/getRequired 双重校验
- 静态 `DATAAGENT_API_TOKEN` 兼容为默认租户 ADMIN
- `PiiService` 按列名识别 EMAIL/PHONE/ID_CARD/BANK_CARD/PERSON_NAME/ADDRESS 并在结果集脱敏
- 字段级血缘从 `alias.column` 解析，`/api/lineage/fields`

详见 [SECURITY.md](SECURITY.md)。

### 3.19 韧性与 Redis

- 数据源熔断：连续失败阈值、30s open、半开由成功 reset
- 瞬时 SQLState `08*/40001/40P01/57P01` 自动重试一次
- 慢查询 >= 5s 对该数据源限流 60s
- Redis 可选：缓存 + `INCR/EXPIRE` 集群配额；不可用回退本地 Semaphore

详见 [RESILIENCE.md](RESILIENCE.md)。

### 3.20 审计导出与 CI

- JSONL 文件 / SIEM webhook / 对象存储 PUT
- 每日 04:30 按 retention-days 清理
- `/v3/api-docs` 契约测试；GitHub Actions 跑后端测试 + Studio 构建 + 产物检查

### 3.21 ETL 与 DolphinScheduler 血缘

- `etl_workflow` / `etl_task`：DolphinScheduler process definition 与 task definition 映射
- `etl_task_dependency`：task relation（preTaskCode/postTaskCode），含 condition/lag
- `etl_workflow_dependency`：跨 workflow 依赖
- SQL 自动解析 source/target 表；taskParams 可覆盖
- 拓扑排序、关键路径、环、孤立任务、nextRun
- `/api/etl/lineage` 端到端表级追踪
- 通用 `/api/lineage/impact` 合并 ETL 任务

详见 [ETL-LINEAGE.md](ETL-LINEAGE.md)。

### 3.22 字段级映射与 OpenLineage

- `FieldMappingParser` 解析 INSERT...SELECT 的 select list、显式目标列、alias.column 和表达式
- `lineage_edge` / `lineage_field_edge` 持久化 ETL 与 OpenLineage 血缘
- 导入 ETL 后自动重建；每小时定时重建
- `POST /api/v1/lineage` 兼容 Marquez，采集 OpenLineage RunEvent
- 自动解析 `facets.columnLineage.fields` → 字段边
- `/api/lineage/impact` 合并 OpenLineage jobs，`/api/lineage/field-impact` 合并字段边

## 4. 数据模型

```mermaid
erDiagram
  DATASOURCE ||--o{ QUERY_EXECUTION : executes
  QUERY_EXECUTION ||--o| KNOWLEDGE_ENTRY : solved_as
  DATASOURCE ||--o{ OPTIMIZATION_EXPERIMENT : benchmarks

  DATASOURCE {
    uuid id PK
    varchar name
    varchar dialect
    varchar jdbc_url
    varchar username
    varchar password_cipher
    boolean read_only
    int max_rows
    int query_timeout_seconds
    timestamp created_at
  }

  QUERY_EXECUTION {
    uuid id PK
    uuid datasource_id FK
    varchar user_id
    varchar kind
    text sql_text
    varchar sql_fingerprint
    varchar status
    bigint duration_ms
    bigint fetch_ms
    int row_count
    boolean truncated
    text error_message
    text plan_raw
    varchar plan_format
    varchar problem
    varchar tags
    boolean solved
    uuid knowledge_id
    timestamp created_at
  }

  KNOWLEDGE_ENTRY {
    uuid id PK
    varchar title
    text problem
    text sql_text
    text optimized_sql
    varchar dialect
    varchar tags
    text plan_summary
    text root_cause
    text optimization_steps
    bigint before_duration_ms
    bigint after_duration_ms
    varchar author
    uuid source_query_id
    int usage_count
    timestamp created_at
    timestamp updated_at
  }

  OPTIMIZATION_EXPERIMENT {
    uuid id PK
    uuid datasource_id FK
    text original_sql
    text candidate_sql
    bigint original_ms
    bigint candidate_ms
    text original_plan
    text candidate_plan
    varchar verdict
    text notes
    timestamp created_at
  }
```

**不持久化结果集**：只保存 SQL 与统计，避免把业务数据复制到知识平台。需要导出时由端侧从当前结果生成 CSV。

V2 新增：

- `sql_asset`：开发历史 SQL 资产（业务问题、次数、耗时、最近计划、知识关联）。
- `semantic_entity` / `semantic_metric` / `semantic_term`：语义层实体、指标、术语。
- `semantic_link`：知识/资产与语义对象的关联，含置信度、原因、AUTO/MANUAL。
- `semantic_sync_run`：定期互通的审计记录。

V4 新增：

- `sql_benchmark_case`：SRE/运维基准 SQL 用例、阈值和最近执行结果。

V5 新增：

- `semantic_relationship`：实体间 join 条件与基数。
- `verified_query`：人工审核的 Q→SQL 目录与最近验证结果。

V6 新增：

- `agent_memory`：长期记忆、作用域、置信度、强化次数、知识提升关联。

V7 新增：

- `memory_candidate`：对话抽取候选与人工审核状态。
- `agent_feedback`：用户纠错、评分与 memory/verified query 关联。

V8 新增：

- `audit_event`：不可变治理审计事件。

V9 新增：

- `api_key` / `api_key_datasource`：分权 Key 与数据源 ACL。
- `datasource.tenant_id`：租户隔离。

V10 新增：

- `etl_workflow` / `etl_task` / `etl_task_dependency` / `etl_workflow_dependency`：ETL 任务与 DolphinScheduler 调度拓扑。

V11 新增：

- `lineage_edge` / `lineage_field_edge`：派生的表级/字段级血缘。
- `openlineage_event`：标准 OpenLineage RunEvent 原始事件。

## 5. 关键流程

### 5.1 执行 SQL

```mermaid
sequenceDiagram
  autonumber
  participant UI as DSH 面板
  participant API as Spring Boot
  participant G as SqlGuard
  participant P as HikariCP
  participant DB as 业务库
  participant H as 执行历史

  UI->>API: POST /api/sql/execute
  API->>G: normalize + 只读校验
  G-->>API: GuardedSql
  API->>P: getConnection()
  P->>DB: 只读连接
  API->>DB: Statement.executeQuery
  DB-->>API: ResultSet + Metadata
  API->>API: 转换值、截断、计时
  opt includePlan
    API->>DB: EXPLAIN <sql>
    DB-->>API: 计划
  end
  API->>H: 保存记录（含计划）
  API-->>UI: rows + columns + elapsed + plan
```

### 5.2 DSH 优化闭环

```mermaid
sequenceDiagram
  autonumber
  participant User as 用户
  participant UI as Studio 面板
  participant API as 云服务
  participant DSH as DSH Agent
  participant MCP as MCP bridge
  participant KB as 知识库

  User->>UI: 点击 AI 优化
  UI->>API: /api/optimize/analyze
  API->>API: EXPLAIN + 规则 + Prompt
  API-->>UI: findings + plan + prompt
  UI-->>User: 展示发现，复制 Prompt
  User->>DSH: 粘贴 Prompt / 追问
  DSH->>MCP: dataagent_explain_sql / describe_table
  MCP->>API: REST
  API-->>MCP: 真实结构/计划
  DSH->>MCP: dataagent_compare_sql
  MCP->>API: 基准对比
  API-->>MCP: 原/候选耗时 + verdict
  DSH-->>User: 诊断 + 候选 SQL + 风险
  User->>UI: 沉淀
  UI->>API: PATCH /history/{id}/solve
  API->>KB: 创建/更新知识条目
```

### 5.3 知识生成

```mermaid
stateDiagram-v2
  [*] --> Executed: 执行成功/失败
  Executed --> Annotated: 添加问题/标签
  Annotated --> Solved: 标记解决 + 根因 + 优化步骤
  Solved --> KnowledgeEntry: 生成/更新知识条目
  KnowledgeEntry --> Reused: 被检索/载入/引用
  Reused --> KnowledgeEntry: usage_count + 1
```

### 5.4 语义层设计助手与定期互通

```mermaid
sequenceDiagram
  autonumber
  participant User as 用户
  participant UI as Studio
  participant API as Spring Boot
  participant SEM as 语义层
  participant KB as 知识库/资产
  participant DSH as DSH

  User->>UI: 输入“新增城市×渠道 GMV 看板”
  UI->>API: POST /api/semantic/design
  API->>SEM: 实体/指标/术语匹配
  SEM-->>API: 匹配结果 + 关联 Link
  API->>KB: 关联知识与 SQL 资产
  API-->>UI: 设计角度/问题/风险/Prompt
  UI->>DSH: 复制 Prompt / 继续对话
  Note over API,SEM: 每 30 分钟或手动触发
  API->>KB: 扫描知识 + 资产
  API->>SEM: 重建 AUTO 关联
  API-->>UI: SyncRun 统计
```

## 6. 端侧 DSH 改造

### 6.1 为什么是插件而不是 fork

DSH 的 Client 是 React + Slot 架构，插件可以注册主面板和侧边栏入口。把 DataAgent 做成 **bundle**：

- 不修改 DSH 安装目录，升级 DSH 不冲突；
- 可独立安装/卸载；
- UI 与 MCP 工具都在一个 bundle 内，安装一次即可；
- 通过 DSH 的 theme/locale/slot 机制融入现有界面。

当前 bundle：

```text
dsh-plugin/
├─ package.json        dsh.bundle.patch + dsh.client
├─ cordis.patch.yml    插入 DataAgent 面板行 + MCP client 行
├─ index.js            Host 入口（空 apply）
├─ client.js           构建后的浏览器 bundle（React external）
├─ locale/zh.json      面板标题/描述
├─ locale/en.json
└─ icon.svg
```

### 6.2 面板能力

- 左侧对象浏览器：schema 切换、表搜索、展开列/主键、点击插入列名、双击插入查询、查询 100 行。
- 中间：多查询页签、SQL 编辑器、行号、运行/计划/AI 优化/格式化/复制、只读标识、耗时/行数状态。
- 结果：数据 / 执行计划 / 消息；CSV 导出、JSON 复制、计划复制；截断标记。
- 右侧：AI 优化（findings、计划摘要、候选 SQL 对比、Prompt）、历史（载入/沉淀）、知识库（详情/载入/引用）、SQL 资产（自动缓存/业务问题/载入）、语义层（实体/指标/术语/设计助手/同步）。
- 设置：云端地址、API Token、主题；连接注册弹窗。

### 6.3 MCP 工具

`mcp-server/server.js` 是零依赖 Node 进程，使用 stdio 换行分隔 JSON-RPC。工具列表：

| 工具 | 作用 |
| --- | --- |
| `dataagent_list_datasources` | 列连接 |
| `dataagent_list_tables` | 列表/过滤 |
| `dataagent_describe_table` | 列 + 主键 + 索引 |
| `dataagent_execute_sql` | 只读执行 + 历史 |
| `dataagent_explain_sql` | 获取计划 |
| `dataagent_analyze_sql` | 规则 + 计划 + Prompt |
| `dataagent_compare_sql` | 基准对比 + 实验记录 |
| `dataagent_search_knowledge` | 查团队知识 |
| `dataagent_save_knowledge` | 手动沉淀 |
| `dataagent_mark_solved` | 从历史沉淀 |
| `dataagent_search_history` | 查历史 |
| `dataagent_get_stats` | 统计 |
| `dataagent_search_sql_assets` | 查 SQL 资产与业务问题 |
| `dataagent_semantic_context` | 查实体/指标/术语及关联 |
| `dataagent_suggest_design` | 新特性设计助手 |
| `dataagent_semantic_overview` | 语义层概览 |
| `dataagent_sync_semantic` | 手动同步语义层 |
| `dataagent_add_semantic_term` | 沉淀术语 |
| `dataagent_add_semantic_metric` | 沉淀指标 |
| `dataagent_ops_overview` | 运维可观测概览 |
| `dataagent_run_sre_benchmarks` | 运行 SRE 基准 |
| `dataagent_search_verified_queries` | 检索已验证 Q→SQL |
| `dataagent_add_verified_query` | 沉淀已验证 Q→SQL |
| `dataagent_validate_verified_query` | 回归验证 |
| `dataagent_search_memory` | 检索长期记忆 |
| `dataagent_remember` | 沉淀长期记忆/提升知识 |
| `dataagent_memory_overview` | 记忆概览 |
| `dataagent_extract_memory_candidates` | 对话抽取候选 |
| `dataagent_review_memory_candidate` | 审核候选 |
| `dataagent_submit_feedback` | 纠错反馈闭环 |
| `dataagent_audit_overview` | 审计概览 |
| `dataagent_audit_search` | 审计检索 |
| `dataagent_lineage_impact` | 表级影响分析 |
| `dataagent_lineage_fields` | 字段级血缘 |
| `dataagent_pii_classify` | PII 分类 |
| `dataagent_etl_overview` | ETL 概览 |
| `dataagent_etl_workflows` | ETL workflow 列表 |
| `dataagent_etl_analysis` | ETL 端到端分析 |
| `dataagent_etl_lineage` | ETL 表级血缘 |
| `dataagent_etl_sync_dolphinscheduler` | DolphinScheduler 导入 |
| `dataagent_etl_field_lineage` | ETL 字段血缘 |
| `dataagent_openlineage_events` | OpenLineage 事件 |
| `dataagent_openlineage_jobs` | OpenLineage job 列表 |
| `dataagent_ingest_openlineage` | 采集 OpenLineage |

所有工具最终受云端的只读网关约束；模型不能通过 MCP 绕过 `SqlGuard`。

### 6.4 为什么没有把 LLM 调用放进 Java

- 大模型能力属于 DSH 的强项：会话、上下文、工具循环、模型路由、配额都在端侧；
- 云端只提供**事实**（计划、耗时、结构）和**知识**，不重复造 Agent；
- 模型升级/切换不影响云服务；
- 敏感 prompt 与模型供应商由企业 DSH 配置决定。

## 7. 安全模型

### 7.1 威胁

| 威胁 | 缓解 |
| --- | --- |
| 误执行 UPDATE/DELETE | 只读关键字白名单 + 数据库只读账号 + 回滚 |
| 多语句注入 | 引号/注释外分号检测 |
| 查询打爆数据库 | 超时 + maxRows + 只读连接；生产加资源组/限流 |
| 密码泄漏 | 端侧不存密码；云端 AES-GCM；密钥独立管理 |
| Token 泄漏 | 可撤销 Token；生产接 OIDC/短期 Token；TLS |
| 数据源 SSRF | URL 白名单；生产限制可访问网段；数据源注册仅管理员 |
| 知识库泄漏敏感 SQL | 脱敏、访问控制、保留策略；不存结果集 |
| Prompt 注入 | SQL 在 Prompt 中标记为“仅数据”；工具仍受后端约束；模型输出不直接执行写入 |

### 7.2 生产加固清单

- [ ] 替换默认 `DATAAGENT_API_TOKEN` 与 `DATAAGENT_SECRET_KEY`
- [ ] 元数据库使用 PostgreSQL，开启备份
- [ ] 数据源使用只读账号
- [ ] 云服务部署在业务库同 VPC/白名单内
- [ ] 接入 OIDC/SSO，按数据源/团队授权
- [ ] 开启 TLS，限制 CORS
- [ ] 增加查询限流、并发上限、审计导出
- [ ] 知识库敏感字段脱敏
- [ ] 关闭 `dataagent.demo.enabled`
- [ ] 数据源创建接口加管理员角色

## 8. 性能与容量

- 每个数据源独立 HikariCP（max 5），避免一个慢库拖垮全部连接。
- 查询结果只保留在请求内存中，默认 500 行；大结果集应分页/导出。
- 历史表按 `created_at`、`datasource_id`、`sql_fingerprint`、`solved` 建索引。
- 计划原文可能很大，限制 200k 字符；列表不返回原文。
- 统计 P95 当前全量拉取耗时，数据量大时改为 SQL 分位/物化统计。
- 生产建议：查询结果流式返回/SSE、异步大查询、结果缓存、每用户并发配额。

## 9. 可观测性

- Spring Boot Actuator：`/actuator/health`、`/actuator/metrics`、`/actuator/prometheus`。
- 日志：查询失败、数据源注册、计划解析降级。
- 建议补充：
  - Micrometer Timer：`dataagent.query.duration`，按 datasource/dialect 标签；
  - 慢查询告警；
  - 审计日志导出到 SIEM；
  - OpenTelemetry trace：端侧请求 → 云端 → JDBC。

## 10. 部署

### 10.1 本地

H2 file 元数据库 + H2 内存演示库，见 README 快速开始。

### 10.2 Docker Compose

```bash
docker compose up --build
```

- `postgres`：元数据库
- `dataagent`：Spring Boot + Studio
- 演示库关闭；通过 UI/API 注册真实数据源

### 10.3 Kubernetes（建议）

- Deployment + Service + Ingress（TLS）
- 元数据库用托管 PostgreSQL/RDS
- Secret 管理 `DATAAGENT_API_TOKEN` / `DATAAGENT_SECRET_KEY`
- HPA 按 CPU/自定义查询并发指标
- NetworkPolicy 限制只访问指定业务库
- 每环境独立知识库/数据源

## 11. 关键取舍

| 决策 | 理由 | 代价 |
| --- | --- | --- |
| 只读优先 | 避免误写、降低权限要求 | 暂不支持数据修改；写入需独立审批通道 |
| 结果不落库 | 降低敏感数据复制风险 | 不能回放历史结果；只能重跑 |
| 计划可选采集 | 减少正常查询开销 | 优化时需额外 EXPLAIN |
| H2 演示库 | 零依赖启动、可测试 | 与真实数据库优化器差异大 |
| MCP 而非把模型放云端 | 复用 DSH 模型/会话/工具 | 需要端侧安装 bundle |
| 单 Token MVP | 快速验证 | 不是 RBAC；生产需 SSO/授权 |
| 规则 + 计划 + LLM | 便宜、可解释、可验证 | 规则覆盖有限；LLM 仍需人工确认 |

## 12. 演进路线

1. **权限**：OIDC、团队/数据源 RBAC、Token 轮换。
2. **查询体验**：流式结果、取消查询、虚拟化网格、语法高亮/补全。
3. **知识库 2.0**：pgvector、LLM 摘要、自动去重、相似问题推荐、知识审核流。
4. **写操作**：单独审批、事务、影响行数预览、回滚脚本。
5. **可观测**：OTel、慢查询告警、审计看板。
6. **生产化**：限流、配额、多租户、灰度、备份恢复演练。

## 13. 与 openLine 的结合（规划）

工作区里已有 `openLine`（字段级血缘）。下一阶段可以把血缘作为 DataAgent 的“上下文增强”：

- 读取 openLine 的元数据/血缘图，补全表、列、上下游关系；
- 优化 SQL 时提示“该字段被哪些下游任务/报表引用”，避免只改性能不看影响面；
- 知识条目关联血缘节点，按表/字段检索历史优化；
- 当前 MVP 不依赖 openLine，二者可独立运行。

## 14. 代码索引

| 关注点 | 文件 |
| --- | --- |
| 只读守卫 | `backend/src/main/java/com/dataagent/sql/SqlGuard.java` |
| 执行与历史 | `backend/src/main/java/com/dataagent/service/JdbcQueryService.java` |
| 计划解析 | `backend/src/main/java/com/dataagent/sql/PlanService.java` |
| 规则分析 | `backend/src/main/java/com/dataagent/sql/SqlAnalyzer.java` |
| 优化编排 | `backend/src/main/java/com/dataagent/service/OptimizationService.java` |
| 知识沉淀 | `backend/src/main/java/com/dataagent/service/HistoryService.java` |
| DSH 面板 | `studio/src/SqlStudio.jsx` + `dsh-plugin/` |
| MCP 工具 | `mcp-server/server.js` |
| 数据模型 | `backend/src/main/resources/db/migration/V1__init.sql` |
