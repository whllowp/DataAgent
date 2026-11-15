# 记忆分层与 Dreaming

参考：[The components of agent memory](https://www.anthropic.com/research/components-of-agent-memory)

DataAgent 把 Anthropic 文章中的四层记忆落到可执行模型：

```text
transcripts (query_execution / 会话日志)
      ↓ dreaming
memory notes (agent_memory, tenant-scoped)
      ↓ merge / correct / promote
wiki representations (memory_wiki_page + links)
      ↓ bounded context injection
injected context (≤ 1600 tokens / top notes + wiki index)
```

## 1. 记忆 note

在原有 `agent_memory` 上增加：

| 字段 | 作用 |
| --- | --- |
| `tenant_id` | 租户隔离 |
| `layer` | `NOTE` / `TRANSCRIPT` |
| `salience` | 进入注入层的综合分 |
| `validated` / `last_validated_at` | 是否经过 transcript/人工确认 |
| `evidence` | transcript id、冲突、验证来源 |
| `supersedes_id` | 新 note 替代旧 note |
| `wiki_slug` | 已提升到哪个 wiki 页面 |

`salience` 综合 importance、confidence、reinforcement、useCount、validated、recency，并降低“当前/今天/临时”这类易失 note 的权重。对应文章中的 **memory can be a prediction about what the future will need**：高价值规则/决策会被提升，易变状态留在 transcript 层。

## 2. Dreaming

`MemoryDreamService.dream()` 是 offline/空闲整理，不被每条会话阻塞：

1. 扫描 active notes 和最近 20 条 transcript
2. 同一 `scope + scopeKey + memoryKey` 下：
   - 相似度 ≥ 0.86：合并内容、reinforcement、confidence
   - 发散/冲突：保留更可信/已验证的 note，旧 note `SUPERSEDED` 并记录 `supersedes_id`
3. 用 transcript 校正 note：
   - SQL/查询模式与近期成功 transcript 对应 → `validated=true`
   - 与近期 failure 冲突 → confidence 下降并写 evidence
   - 易失状态降权
4. 重新计算 salience
5. 高 salience note 提升为 wiki page，并生成 `index` 页 + 链接
6. 构建 top notes + index 的受限注入快照
7. 写 `memory_dream_run` 统计和审计

API：

```http
POST /api/memory/dream
GET  /api/memory/dreams
GET  /api/memory/wiki
GET  /api/memory/wiki/{slug}
GET  /api/memory/context?q=&sessionId=&domain=&datasource=&user=
POST /api/memory/{id}/validate
```

`POST /api/memory/dream` 也支持定时任务：

```yaml
dataagent:
  memory:
    dream-cron: "0 30 3 * * *"
```

## 3. Wiki 表示

`memory_wiki_page` 存 title、summary、Markdown content、pageType、tokenEstimate、sourceMemoryIds。

`memory_wiki_link` 存页面关系：

- `INDEXES`：index → topic/playbook/preference
- `RELATED`：同类型页面之间

页面类型：

- `PLAYBOOK`（决策/模式）
- `PREFERENCE`
- `OPEN_QUESTION`
- `TOPIC`
- `INDEX`

对应文章里的 **wiki helps navigate memory**：wiki 不是把所有内容再抄一遍，而是用索引和链接把高价值 note 组织成可搜索地图。

## 4. 注入上下文

`GET /api/memory/context` 返回：

- `injected`：按 salience 排序的 top notes，受 1600 token 预算限制
- `notes`：同一批可读 note
- `wiki`：匹配的 wiki 页面
- `transcripts`：匹配的最近查询日志（只给摘要，不给全量 SQL）
- `tokenEstimate` / `tokenBudget`

每次 context 读取都会给 note 增加 `useCount`/`lastUsedAt`，让 dreaming 的 salience 反映真实使用频率。

## 5. 与知识库的区别

- memory：轻量、可强化、可过期、可被 dreaming 改写
- knowledge：结构化、可搜索、面向团队知识资产
- semantic/ontology：业务术语、字段映射、SQL attributes、join

记忆在 dreaming 后可 `promoteToKnowledge`，但不会自动替人做业务认证。

## 6. 边界

- dreaming 的“校正”是基于 transcript 的启发式，不是完整语义事实核验。
- 合并/替代会保留 `SUPERSEDED` 记录，当前 UI 默认展示 ACTIVE。
- 注入预算按字符/4 估算 token，不依赖具体模型 tokenizer。
- 还未实现跨 session transcript 的全文检索排序（当前用最近日志 + embedding）。
- wiki 自动生成后仍建议人工审核关键业务口径。
