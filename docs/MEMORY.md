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

MCP：

- `dataagent_search_memory`
- `dataagent_remember`
- `dataagent_memory_overview`

## 7. 治理建议

- 业务决策由 owner 确认后再写入 `DECISION`
- OPEN_QUESTION 不应当作事实使用
- 定期把高价值记忆提升为知识库或 Verified Query
- 对过期/低置信记忆只归档不删除，保留审计
- 后续可加 embedding 召回、冲突检测和人工审核队列
