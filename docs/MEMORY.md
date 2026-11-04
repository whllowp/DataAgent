# 长期记忆与知识库沉淀

> 目标：让 Agent 跨会话记住“团队怎么做事”，并把这些共识持续沉淀到知识库/语义层，而不是每次从零开始。

## 1. 记忆分层

| 类型 | 说明 | 例子 |
| --- | --- | --- |
| PREFERENCE | 用户/团队偏好 | GMV 默认按支付成功订单；日期用半开区间 |
| DECISION | 已确认的业务决策 | 客单价分母使用支付客户数 |
| FACT | 稳定事实/环境信息 | Demo 数据源是 H2 销售库 |
| PATTERN | 可复用的 SQL/排查模式 | 订单→客户 join 使用 `customer_id` |
| OPEN_QUESTION | 待确认问题 | 渠道为空是否归入 UNKNOWN |

作用域：

- `GLOBAL`：所有会话可用
- `DOMAIN`：业务域，如“销售”
- `DATASOURCE`：特定数据源
- `USER`：个人偏好

## 2. 写入与强化

`POST /api/memory`：

```json
{
  "scope": "DOMAIN",
  "scopeKey": "销售",
  "kind": "DECISION",
  "key": "aov.denominator",
  "content": "客单价分母使用支付客户数",
  "confidence": 80,
  "importance": 70,
  "promoteToKnowledge": false
}
```

同一 `scope + scopeKey + key` 不重复插入，而是：

- 更新内容
- confidence 提升（最高 100）
- reinforcementCount +1
- importance 取较高值

这让 Agent 可以“反复确认同一件事”，而不是制造重复记忆。

## 3. 检索与 Agent 上下文

`GET /api/memory?q=&scope=&kind=` 用于显式检索。

`GET /api/semantic/context` 和 `POST /api/semantic/design` 会带出：

- GLOBAL 记忆
- 当前业务域的记忆
- 与问题 token 匹配的记忆（最多 8 条）

设计 Prompt 新增「长期记忆」一节，模型可以复用已确认口径，而不是重复询问。

## 4. 知识库沉淀

`promoteToKnowledge=true` 且提供 `sqlText` 时：

1. 调用知识库服务创建 `knowledge_entry`
2. 记忆记录 `knowledge_id`
3. 返回 `memory + knowledge`

这样一次对话可以同时完成：

- 长期记忆：轻量、可强化、可过期
- 知识库：结构化、可搜索、可引用
- 语义层：可复用的指标/术语/关系
- Verified Query：可回归的标准 Q→SQL

## 4.1 对话结束自动抽取候选

`POST /api/memory/candidates/extract` 接收会话摘要或消息列表：

```json
{
  "sessionId": "s-123",
  "domain": "销售",
  "summary": "以后 GMV 默认按支付成功订单统计。",
  "messages": [
    {"role": "user", "content": "以后 GMV 默认按支付成功订单统计"},
    {"role": "assistant", "content": "已记录"}
  ]
}
```

抽取规则：

- 按句子切分，命中「默认/口径/定义/必须/不要/待确认/是否/统一/以后/记住/决策/规则」等线索
- 自动推断 PREFERENCE / DECISION / PATTERN / OPEN_QUESTION / FACT
- 生成 `auto.<hash>` 记忆 key 与 content hash
- **只写入 `memory_candidate`，状态 PENDING，不直接进入记忆/上下文**

审核：

- `GET /api/memory/candidates?status=PENDING`
- `POST /api/memory/candidates/{id}/approve`
- `POST /api/memory/candidates/{id}/reject`

UI 的「记忆」页签展示待审核候选，支持通过 / 强制通过 / 拒绝。

## 4.2 冲突检测

在候选审核或写入同一 `scope+key` 时，比较新旧内容的 embedding：

- 相似度低于阈值，或一方含「不是/不要/不能/禁止/不再」而另一方没有
- 返回 `conflicts`，审核接口默认不通过
- 审核人确认后可 `force=true` 强制通过

## 4.3 Embedding 召回

`EmbeddingService` 使用本地、无依赖的 hashed word/character n-gram + cosine：

- 语义层实体/指标/术语检索
- 长期记忆召回
- Verified Query 召回

它替代了原先的纯 tokenize 匹配；后续可替换为远程 embedding 模型，而不改调用方。

## 4.4 反馈闭环

`POST /api/feedback`：

```json
{
  "sessionId": "s-123",
  "question": "按城市统计 GMV",
  "originalSql": "SELECT city FROM sales_orders",
  "correctedSql": "SELECT city, SUM(amount) FROM sales_orders WHERE status = 'PAID' GROUP BY city",
  "correction": "按城市统计 GMV 时必须过滤 PAID",
  "domain": "销售",
  "rating": 5
}
```

一次调用完成：

1. 记录 `agent_feedback`
2. 写入/强化 `agent_memory`（source=FEEDBACK）
3. 若有 `correctedSql`，创建 `DRAFT` verified query
4. 返回 memory、verified query、conflicts

MCP：`dataagent_submit_feedback`，DSH 可在用户点踩/纠错时直接调用。

## 5. 生命周期

- `status`: ACTIVE / ARCHIVED
- `expiresAt`: 可选过期时间
- 每日 03:15 自动归档过期记忆
- `POST /api/memory/{id}/archive` 手动归档
- `POST /api/memory/{id}/use` 记录使用次数
- `POST /api/memory/archive-expired` 手动清理

## 6. API / MCP

REST：

| Method | Path |
| --- | --- |
| GET | `/api/memory` |
| GET | `/api/memory/overview` |
| POST | `/api/memory` |
| PUT/DELETE | `/api/memory/{id}` |
| POST | `/api/memory/{id}/use` |
| POST | `/api/memory/{id}/archive` |
| POST | `/api/memory/archive-expired` |

候选审核：

| Method | Path |
| --- | --- |
| GET | `/api/memory/candidates?status=PENDING` |
| POST | `/api/memory/candidates/extract` |
| POST | `/api/memory/candidates/{id}/approve` |
| POST | `/api/memory/candidates/{id}/reject` |

反馈闭环：

| Method | Path |
| --- | --- |
| POST | `/api/feedback` |
| GET | `/api/feedback` |

MCP：

- `dataagent_search_memory`
- `dataagent_remember`
- `dataagent_memory_overview`
- `dataagent_extract_memory_candidates`
- `dataagent_review_memory_candidate`
- `dataagent_submit_feedback`

## 7. 治理建议

- 业务决策由 owner 确认后再写入 `DECISION`
- OPEN_QUESTION 不应当作事实使用
- 定期把高价值记忆提升为知识库或 Verified Query
- 对过期/低置信记忆只归档不删除，保留审计
- 候选审核与反馈纠错必须保留 reviewedBy/reviewedAt
- 远程 embedding 模型可作为本地 hashed embedding 的替换实现
