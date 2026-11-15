# Auto-Ontology 风格语义层与血缘

参考：[NVIDIA Auto Ontology](https://github.com/NVIDIA/auto-ontology)

目标是让 Agent 在写 SQL 前知道：

- 数据在哪里（catalog / table / column）
- 业务含义（term / synonyms / definition）
- 怎么用（SQL attributes、grain、join）
- 是否可信（status、confidence、provenance）
- 来自哪里（metadata / transcript / verified query / manual）
- 相似/相关对象（join path、graph）

## 1. 核心对象

| 对象 | 表 | 说明 |
| --- | --- | --- |
| Term | `semantic_term` | 业务概念/表级 term，带定义、同义词、owner |
| Term column | `semantic_term_column` | term 到物理 `schema.table.column` 的映射，role=KEY/FOREIGN_KEY/ATTRIBUTE |
| SQL attribute | `semantic_sql_attribute` | term 下经过审核的 SQL 表达式、grain、provenance；可来自 metric |
| Semantic join | `semantic_join` | term 之间的 join condition、基数、confidence、source、status |
| Compile run | `semantic_compile_run` | 一次 metadata 编译的统计与错误 |

原有 `semantic_entity`、`semantic_metric`、`semantic_relationship`、`verified_query` 继续保留。

## 2. 编译：metadata → ontology

```http
POST /api/semantic/compile
{"datasourceId":"...","schema":"PUBLIC","mode":"FULL"}
```

编译步骤：

1. `MetadataService.listTables` / `describeTable` 读取表、列、主键
2. 创建 `Entity` + `Term`（DRAFT）
3. 每列生成 `semantic_term_column`
   - 主键 → `KEY`
   - `*_ID` → `FOREIGN_KEY`
   - 其它 → `ATTRIBUTE`
4. 通过列名推断 join：
   - `customer_id` → table `customers` / `customer`
   - `customer_id` = `customers.id`
   - 默认 `MANY_TO_ONE`、confidence 75、status DRAFT
5. 把已有 metric 的 expression 复制为 term 下的 SQL attribute（DRAFT）
6. 写 compile run 统计

不会自动把 DRAFT 当成 CERTIFIED；Agent 使用 DRAFT 时会在 provenance/answerability 中显式暴露。

## 3. 答案可回答性

```http
GET /api/semantic/ontology/answerable?q=客户贡献的收入
```

返回：

- `answerable`
- `coverage`：问题词被 term 覆盖率
- `terms` / `columns` / `sqlAttributes` / `joins`
- `missingConcepts`
- `explanation`

Agent 应先调用 `answerable`，coverage 不足时澄清问题，而不是直接猜 SQL。对应 Auto Ontology 的 `check_answerable`。

## 4. Join path

```http
GET /api/semantic/ontology/joins/path?from=<termId>&to=<termId>
```

BFS 在 `semantic_join` 上找无向最短路径，返回 join condition 和 confidence。对应 Auto Ontology 的 deterministic join path。

## 5. 认证

```http
POST /api/semantic/ontology/columns/{id}/certify
POST /api/semantic/ontology/attributes/{id}/certify
POST /api/semantic/ontology/joins/{id}/certify
{"status":"CERTIFIED","owner":"data-team","note":"reviewed"}
```

CERTIFIED 映射/属性/join 的 confidence 提升到 95，并把认证人/备注写入 provenance。

## 6. 图与上下文

```http
GET /api/semantic/ontology/graph
GET /api/semantic/ontology/graph?termId=...
GET /api/semantic/ontology/columns?termId=...|table=...
GET /api/semantic/ontology/sql-attributes?q=...
```

图节点：

- `TERM`
- `COLUMN`
- `SQL_ATTRIBUTE`
- 边：`HAS_COLUMN` / `HAS_ATTRIBUTE` / `JOIN`

## 7. 血缘分析层

`/api/lineage/impact` 和 `/api/lineage/field-impact` 现在额外返回：

- `semanticTerms`
- `termColumns`
- `sqlAttributes`

`/api/semantic/ontology/impact?table=&column=` 提供语义视角的影响面。

因此一次变更分析可以同时覆盖：

```text
物理表/字段
  → 语义 term
  → term column 映射
  → SQL attribute
  → semantic join
  → 下游 SQL 资产 / Verified Query / ETL / OpenLineage
```

## 8. MCP

- `dataagent_ontology_compile`
- `dataagent_ontology_answerable`
- `dataagent_ontology_join_path`
- `dataagent_ontology_impact`
- `dataagent_ontology_graph`

## 9. 边界

- join 推断基于列名和主键，不是完整 FK 约束/数据分布验证；因此默认 DRAFT。
- SQL attribute 目前从 metric expression 复制，未自动从 query history 归纳。
- 未实现 Ossie YAML 全量导入/导出和 bridge-table SQL attribute 自动生成。
- `coverage` 是启发式 token 覆盖率，不是完整语义蕴含。
- 认证后的 ontology 仍建议用真实问题做 smoke benchmark 回归。
