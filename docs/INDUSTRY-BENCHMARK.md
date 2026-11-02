# 业界 Data Agent 对标与优化路线

> 目标：不做“只会生成 SQL 的聊天框”，而是把 Data Agent 做成可治理、可评测、可回归的数据产品。

## 1. 业界产品收敛出的能力

公开资料里，Databricks Genie、Snowflake Cortex Analyst/Agents、Omni 等产品的方向高度一致：

| 能力 | 业界做法 | DataAgent 现状 |
| --- | --- | --- |
| 语义层 / Ontology | 实体、指标、术语、关系、认证数据集；Genie Ontology 把业务对象与关系显式化 | 已有实体/指标/术语；本轮新增 **实体关系（join 条件、基数）** |
| Verified Queries | 人工审核的 Q→SQL，作为高置信示例与回归基线 | 本轮新增 **已验证查询目录 + 只读验证** |
| 语义检索 | 按问题检索指标、实体、关系、相似 SQL | 已有 tokenized 检索；本轮把关系/已验证查询注入上下文 |
| 计划与工具调用 | 规划、取数、解释、校验、对比 | DSH + MCP 工具链已有；新增 verified query 工具 |
| 可观测 | 查询耗时、失败、慢查询、连接池、调度 | 已有 `/api/ops/overview` 与 Prometheus |
| 评测 / 回归 | Golden questions、AI evals、结果对账 | 已有 SRE 基准；NL2SQL 评测仍是下一步 |
| 反馈闭环 | 点赞/点踩、纠错、沉淀为 verified query | 本轮提供 verified query 沉淀 API/MCP；反馈表待补 |
| 治理 | 权限、审计、PII、只读、成本上限 | 只读网关 + Token；RBAC/行级权限待补 |

## 2. 本轮落地

### 2.1 实体关系（Ontology）

新增 `semantic_relationship`：

- `from_entity_id` / `to_entity_id`
- `relationship_type`：ONE_TO_ONE / ONE_TO_MANY / MANY_TO_ONE / MANY_TO_MANY
- `join_condition`：例如 `sales_orders.customer_id = customers.id`
- `description`、`synonyms`、`owner`、`status`

作用：

- 语义上下文返回与命中实体相关的关系；
- 设计助手明确 JOIN 路径与一对多放大风险；
- 为后续 NL2SQL 提供确定性的 join graph，而不是让模型猜。

### 2.2 Verified Query 目录

新增 `verified_query`：

- 问题、SQL、数据源、关联指标/实体、标签、owner、notes
- `status`：VERIFIED / DRAFT / DEPRECATED
- `last_validation_status`、`last_duration_ms`、`last_error`、`use_count`

能力：

- 检索已验证 Q→SQL，作为 few-shot 示例和口径基线；
- `POST /api/verified-queries/{id}/validate` 只读执行并记录结果；
- 语义上下文 / 设计助手自动带出相关 verified queries；
- MCP 新增搜索、沉淀、验证工具。

### 2.3 与 Agent 上下文打通

`GET /api/semantic/context` 和 `POST /api/semantic/design` 现在返回：

- `relationships`：实体关系与 join 条件
- `verifiedQueries`：相关已验证 Q→SQL

设计 Prompt 增加「实体关系（Ontology）」「已验证 Q→SQL」两节，并明确：

- 一对多 JOIN 可能放大；
- 缺少关系时不能猜 join；
- 缺少 verified query 时准确率与可回归性不足。

## 3. 下一步优先级

1. **Agent trace + feedback**：记录问题、检索上下文、生成 SQL、工具调用、用户反馈；把纠错一键升级为 verified query。
2. **NL2SQL golden set**：问题 → 期望 SQL/结果 → 自动跑分；纳入 CI。
3. **字段级血缘**：复用工作区 `openLine`，把变更影响面注入优化与设计上下文。
4. **RBAC / 行级权限 / PII**：按数据源、指标、字段授权，支持脱敏。
5. **向量检索**：语义层 + verified query 的 embedding 召回，替换纯关键词。

## 4. 参考

- [Databricks: Introducing Genie One, Genie Agents, and Genie Ontology](https://www.databricks.com/blog/introducing-genie-one-genie-ontology-and-genie-agents)
- [Snowflake: Semantic views — verified queries](https://docs.snowflake.cn/en/release-notes/2026/other/2026-04-05-semantic-views-verified-queries)
- [Snowflake: Build Agentic Analytics with Semantic Views](https://www.snowflake.com/en/developers/guides/snowflake-semantic-view-agentic-analytics/)
- [Omni: Run your agent like a data product with AI Evals](https://omni.co/blog/run-your-agent-like-a-data-product-with-ai-evals)
- [Microsoft Learn: Analyze backlog and user feedback for AI agents](https://learn.microsoft.com/ja-jp/training/modules/analyze-monitor-tune-ai-powered-business-solutions/3-analyze-backlog-user-feedback-ai-agent-usage)

> 以上链接仅作为行业方向参考；具体功能以各产品官方文档为准。
