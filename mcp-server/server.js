#!/usr/bin/env node
/**
 * Minimal MCP server (stdio, newline-delimited JSON-RPC) that exposes the
 * DataAgent cloud API as agent tools. No third-party dependencies: it runs with
 * the Node.js bundled by DSH and can be launched directly from cordis.patch.yml.
 */
import readline from 'node:readline';

const BASE_URL = (process.env.DATAAGENT_URL || 'http://127.0.0.1:8080').replace(/\/+$/, '');
const TOKEN = process.env.DATAAGENT_TOKEN || 'dataagent-dev-token';
const PROTOCOL_VERSION = '2024-11-05';
const SERVER_INFO = { name: 'dataagent', version: '0.1.0' };

function log(...args) {
  process.stderr.write(`[dataagent-mcp] ${args.map(String).join(' ')}\n`);
}

function send(message) {
  process.stdout.write(`${JSON.stringify(message)}\n`);
}

function ok(id, result) {
  send({ jsonrpc: '2.0', id, result });
}

function fail(id, code, message, data) {
  send({ jsonrpc: '2.0', id, error: { code, message, ...(data === undefined ? {} : { data }) } });
}

async function api(path, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), options.timeout ?? 90_000);
  try {
    const response = await fetch(`${BASE_URL}${path}`, {
      method: options.method || 'GET',
      headers: {
        'Content-Type': 'application/json',
        'X-API-Key': TOKEN,
        'X-User-Id': process.env.DATAAGENT_USER || 'dsh-agent',
      },
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal,
    });
    const text = await response.text();
    let payload;
    try {
      payload = text ? JSON.parse(text) : null;
    } catch {
      payload = { raw: text };
    }
    if (!response.ok) {
      const message = payload && payload.message ? payload.message : `HTTP ${response.status}`;
      const error = new Error(message);
      error.status = response.status;
      error.payload = payload;
      throw error;
    }
    return payload;
  } catch (error) {
    if (error.name === 'AbortError') {
      const timeoutError = new Error('DataAgent 云端请求超时');
      timeoutError.code = 'TIMEOUT';
      throw timeoutError;
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

const tools = [
  {
    name: 'dataagent_list_datasources',
    description: '列出云端已注册的数据源（连接名、方言、只读状态、默认行数/超时）。开始分析前先用它确认 datasourceId。',
    inputSchema: { type: 'object', properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: true },
    handler: () => api('/api/datasources'),
  },
  {
    name: 'dataagent_list_tables',
    description: '列出某个数据源指定 schema 下的表/视图，可按名称过滤。用于了解可用表。',
    inputSchema: {
      type: 'object',
      properties: {
        datasourceId: { type: 'string', description: '数据源 UUID；省略时使用第一个数据源' },
        schema: { type: 'string', description: 'schema；省略时使用默认 schema' },
        query: { type: 'string', description: '按表名模糊过滤' },
      },
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: async (args) => {
      const datasourceId = args.datasourceId || await firstDatasourceId();
      const schema = args.schema || await firstSchema(datasourceId);
      return api(`/api/datasources/${datasourceId}/tables?schema=${encodeURIComponent(schema)}&q=${encodeURIComponent(args.query || '')}`);
    },
  },
  {
    name: 'dataagent_describe_table',
    description: '读取表的列、类型、可空性、主键和索引。优化前必须确认表结构与索引，不要臆造。',
    inputSchema: {
      type: 'object',
      properties: {
        datasourceId: { type: 'string', description: '数据源 UUID；省略时使用第一个数据源' },
        schema: { type: 'string', description: 'schema；省略时使用默认 schema' },
        table: { type: 'string', description: '表名（必填）' },
      },
      required: ['table'],
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: async (args) => {
      const datasourceId = args.datasourceId || await firstDatasourceId();
      const schema = args.schema || await firstSchema(datasourceId);
      return api(`/api/datasources/${datasourceId}/tables/${encodeURIComponent(schema)}/${encodeURIComponent(args.table)}`);
    },
  },
  {
    name: 'dataagent_execute_sql',
    description: '通过云端只读网关执行 SELECT/WITH/VALUES 查询，返回列、行、耗时、截断标记并记录历史。生产排查请限制 maxRows。',
    inputSchema: {
      type: 'object',
      properties: {
        datasourceId: { type: 'string', description: '数据源 UUID；省略时使用第一个数据源' },
        sql: { type: 'string', description: '只读 SQL' },
        maxRows: { type: 'integer', minimum: 1, maximum: 5000, default: 100 },
        timeoutSeconds: { type: 'integer', minimum: 1, maximum: 60 },
      },
      required: ['sql'],
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: async (args) => {
      const datasourceId = args.datasourceId || await firstDatasourceId();
      return api('/api/sql/execute', {
        method: 'POST',
        body: {
          datasourceId,
          sql: args.sql,
          maxRows: args.maxRows || 100,
          timeoutSeconds: args.timeoutSeconds,
          includePlan: false,
        },
      });
    },
  },
  {
    name: 'dataagent_explain_sql',
    description: '只获取执行计划，不执行查询。返回原始计划、摘要和计划洞察；只允许 SELECT/WITH/VALUES。',
    inputSchema: {
      type: 'object',
      properties: {
        datasourceId: { type: 'string', description: '数据源 UUID；省略时使用第一个数据源' },
        sql: { type: 'string', description: '只读 SQL' },
      },
      required: ['sql'],
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: async (args) => {
      const datasourceId = args.datasourceId || await firstDatasourceId();
      return api('/api/sql/explain', { method: 'POST', body: { datasourceId, sql: args.sql } });
    },
  },
  {
    name: 'dataagent_analyze_sql',
    description: '静态规则 + 执行计划联合分析，返回 findings、计划、DSH 提示词和约束。适合优化起点。',
    inputSchema: {
      type: 'object',
      properties: {
        datasourceId: { type: 'string', description: '数据源 UUID；省略时使用第一个数据源' },
        sql: { type: 'string', description: '只读 SQL' },
        includePlan: { type: 'boolean', default: true },
      },
      required: ['sql'],
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true, idempotentHint: true },
    handler: async (args) => {
      const datasourceId = args.datasourceId || await firstDatasourceId();
      return api('/api/optimize/analyze', {
        method: 'POST',
        body: { datasourceId, sql: args.sql, includePlan: args.includePlan !== false },
      });
    },
  },
  {
    name: 'dataagent_compare_sql',
    description: '在云端比较原 SQL 与候选 SQL 的执行计划与耗时，并记录实验。候选必须只读；务必向用户说明语义等价性风险。',
    inputSchema: {
      type: 'object',
      properties: {
        datasourceId: { type: 'string', description: '数据源 UUID；省略时使用第一个数据源' },
        originalSql: { type: 'string' },
        candidateSql: { type: 'string' },
        runs: { type: 'integer', minimum: 1, maximum: 5, default: 2 },
        execute: { type: 'boolean', default: true, description: 'true 时真实执行并取最小耗时；false 时只对比计划' },
      },
      required: ['originalSql', 'candidateSql'],
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: async (args) => {
      const datasourceId = args.datasourceId || await firstDatasourceId();
      return api('/api/optimize/compare', {
        method: 'POST',
        body: {
          datasourceId,
          originalSql: args.originalSql,
          candidateSql: args.candidateSql,
          runs: args.runs || 2,
          execute: args.execute !== false,
          notes: 'DSH MCP compare',
        },
      });
    },
  },
  {
    name: 'dataagent_search_knowledge',
    description: '检索团队知识库（问题、SQL、根因、优化步骤、标签）。优化前先查重，避免重复踩坑。',
    inputSchema: {
      type: 'object',
      properties: {
        query: { type: 'string', description: '全文关键词' },
        dialect: { type: 'string', description: '可选方言：H2/POSTGRESQL/MYSQL' },
        size: { type: 'integer', minimum: 1, maximum: 50, default: 10 },
      },
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: (args) => {
      const params = new URLSearchParams();
      if (args.query) params.set('q', args.query);
      if (args.dialect) params.set('dialect', args.dialect);
      params.set('size', String(args.size || 10));
      return api(`/api/knowledge?${params.toString()}`);
    },
  },
  {
    name: 'dataagent_save_knowledge',
    description: '把已验证的问题、SQL、根因、优化步骤保存到团队知识库。只应在用户确认结论后调用。',
    inputSchema: {
      type: 'object',
      properties: {
        title: { type: 'string' },
        problem: { type: 'string' },
        sqlText: { type: 'string' },
        optimizedSql: { type: 'string' },
        dialect: { type: 'string' },
        tags: { type: 'string', description: '逗号分隔' },
        planSummary: { type: 'string' },
        rootCause: { type: 'string' },
        optimizationSteps: { type: 'string' },
        beforeDurationMs: { type: 'integer' },
        afterDurationMs: { type: 'integer' },
        sourceQueryId: { type: 'string' },
      },
      required: ['title', 'sqlText'],
      additionalProperties: false,
    },
    handler: (args) => api('/api/knowledge', { method: 'POST', body: args }),
  },
  {
    name: 'dataagent_mark_solved',
    description: '把一条执行历史标记为解决，并生成/更新知识条目。用于把一次真实排查沉淀到团队知识库。',
    inputSchema: {
      type: 'object',
      properties: {
        queryId: { type: 'string' },
        title: { type: 'string' },
        problem: { type: 'string' },
        rootCause: { type: 'string' },
        optimizationSteps: { type: 'string' },
        optimizedSql: { type: 'string' },
        tags: { type: 'string' },
        afterDurationMs: { type: 'integer' },
      },
      required: ['queryId'],
      additionalProperties: false,
    },
    handler: (args) => api(`/api/history/${encodeURIComponent(args.queryId)}/solve`, {
      method: 'PATCH',
      body: {
        title: args.title,
        problem: args.problem,
        rootCause: args.rootCause,
        optimizationSteps: args.optimizationSteps,
        optimizedSql: args.optimizedSql,
        tags: args.tags,
        afterDurationMs: args.afterDurationMs,
      },
    }),
  },
  {
    name: 'dataagent_search_history',
    description: '检索最近的执行历史，包含耗时、计划、问题描述和是否已沉淀。用于定位可复用的历史查询。',
    inputSchema: {
      type: 'object',
      properties: {
        query: { type: 'string' },
        datasourceId: { type: 'string' },
        solved: { type: 'boolean' },
        size: { type: 'integer', minimum: 1, maximum: 50, default: 10 },
      },
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: (args) => {
      const params = new URLSearchParams();
      if (args.query) params.set('q', args.query);
      if (args.datasourceId) params.set('datasourceId', args.datasourceId);
      if (typeof args.solved === 'boolean') params.set('solved', String(args.solved));
      params.set('size', String(args.size || 10));
      return api(`/api/history?${params.toString()}`);
    },
  },
  {
    name: 'dataagent_get_stats',
    description: '获取云端查询统计：今日查询数、平均/P95 耗时、慢查询、已解决数、知识条目数。',
    inputSchema: { type: 'object', properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: true },
    handler: () => api('/api/stats/overview'),
  },
  {
    name: 'dataagent_search_sql_assets',
    description: '检索团队 SQL 资产缓存：开发历史 SQL、它解决的业务问题、执行次数与平均耗时。比执行历史更接近“知识资产”。',
    inputSchema: {
      type: 'object',
      properties: {
        query: { type: 'string', description: '业务问题 / SQL / 标签关键词' },
        domain: { type: 'string', description: '业务域，例如 销售 / 财务' },
        datasourceId: { type: 'string' },
        size: { type: 'integer', minimum: 1, maximum: 100, default: 20 },
      },
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: (args) => {
      const params = new URLSearchParams();
      if (args.query) params.set('q', args.query);
      if (args.domain) params.set('domain', args.domain);
      if (args.datasourceId) params.set('datasourceId', args.datasourceId);
      params.set('size', String(args.size || 20));
      return api(`/api/sql-assets?${params.toString()}`);
    },
  },
  {
    name: 'dataagent_semantic_context',
    description: '查询业务语义层：匹配实体、指标、术语，并返回关联的团队知识和历史 SQL 资产。写 SQL 或设计新功能前先调用。',
    inputSchema: {
      type: 'object',
      properties: {
        query: { type: 'string', description: '业务关键词，例如 GMV 城市 渠道' },
        domain: { type: 'string', description: '业务域' },
      },
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: (args) => {
      const params = new URLSearchParams();
      if (args.query) params.set('q', args.query);
      if (args.domain) params.set('domain', args.domain);
      return api(`/api/semantic/context?${params.toString()}`);
    },
  },
  {
    name: 'dataagent_suggest_design',
    description: '基于语义层与知识库为新特性/新业务生成设计思路、待确认问题、风险与可交给 DSH 继续完善的 Prompt。',
    inputSchema: {
      type: 'object',
      properties: {
        idea: { type: 'string', description: '业务想法/需求描述' },
        domain: { type: 'string' },
        datasourceId: { type: 'string' },
      },
      required: ['idea'],
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: (args) => api('/api/semantic/design', {
      method: 'POST',
      body: { idea: args.idea, domain: args.domain, datasourceId: args.datasourceId },
    }),
  },
  {
    name: 'dataagent_semantic_overview',
    description: '查看语义层规模、关联数量、最近一次知识库同步记录。',
    inputSchema: { type: 'object', properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: true },
    handler: () => api('/api/semantic/overview'),
  },
  {
    name: 'dataagent_sync_semantic',
    description: '手动触发语义层与知识库/SQL 资产的互通同步。会重建 AUTO 关联，保留人工关联；应在用户明确要求时调用。',
    inputSchema: { type: 'object', properties: {}, additionalProperties: false },
    handler: () => api('/api/semantic/sync', { method: 'POST' }),
  },
  {
    name: 'dataagent_add_semantic_term',
    description: '沉淀一个业务术语（定义、同义词、标签，可关联实体/指标）。用于把对话中的业务共识写回语义层。',
    inputSchema: {
      type: 'object',
      properties: {
        term: { type: 'string' },
        definition: { type: 'string' },
        synonyms: { type: 'string' },
        relatedEntityId: { type: 'string' },
        relatedMetricId: { type: 'string' },
        tags: { type: 'string' },
      },
      required: ['term'],
      additionalProperties: false,
    },
    handler: (args) => api('/api/semantic/terms', { method: 'POST', body: args }),
  },
  {
    name: 'dataagent_add_semantic_metric',
    description: '沉淀一个指标（业务定义、表达式、维度、同义词），让后续 SQL 和新功能复用统一口径。',
    inputSchema: {
      type: 'object',
      properties: {
        name: { type: 'string' },
        displayName: { type: 'string' },
        entityId: { type: 'string' },
        businessDefinition: { type: 'string' },
        expression: { type: 'string' },
        dimensions: { type: 'string' },
        synonyms: { type: 'string' },
        tags: { type: 'string' },
      },
      required: ['name', 'displayName'],
      additionalProperties: false,
    },
    handler: (args) => api('/api/semantic/metrics', { method: 'POST', body: args }),
  },
  {
    name: 'dataagent_ops_overview',
    description: '获取运维可观测概览：查询耗时/失败/慢查询、连接池、JVM、语义同步调度和 SRE 基准状态。',
    inputSchema: { type: 'object', properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: true },
    handler: () => api('/api/ops/overview'),
  },
  {
    name: 'dataagent_run_sre_benchmarks',
    description: '运行全部 ACTIVE 的 SRE SQL 基准用例，返回成功/失败数量；只读执行并自动更新基准状态。',
    inputSchema: { type: 'object', properties: {}, additionalProperties: false },
    handler: () => api('/api/benchmarks/run', { method: 'POST' }),
  },
  {
    name: 'dataagent_search_verified_queries',
    description: '检索已审核的 Q→SQL（verified queries），作为语义层和少样本示例，提升生成 SQL 的准确率。',
    inputSchema: {
      type: 'object',
      properties: {
        query: { type: 'string', description: '业务问题 / SQL / 标签关键词' },
      },
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: (args) => {
      const params = new URLSearchParams();
      if (args.query) params.set('q', args.query);
      return api(`/api/verified-queries?${params.toString()}`);
    },
  },
  {
    name: 'dataagent_add_verified_query',
    description: '沉淀一条经过人工确认的 Q→SQL，供后续 NL2SQL、语义上下文和回归验证复用。',
    inputSchema: {
      type: 'object',
      properties: {
        question: { type: 'string' },
        sqlText: { type: 'string' },
        datasourceId: { type: 'string' },
        metricId: { type: 'string' },
        entityId: { type: 'string' },
        tags: { type: 'string' },
        notes: { type: 'string' },
      },
      required: ['question', 'sqlText'],
      additionalProperties: false,
    },
    handler: (args) => api('/api/verified-queries', { method: 'POST', body: args }),
  },
  {
    name: 'dataagent_validate_verified_query',
    description: '只读执行一条已验证查询并记录结果、列名和耗时；用于回归验证语义层口径是否失效。',
    inputSchema: {
      type: 'object',
      properties: { id: { type: 'string' } },
      required: ['id'],
      additionalProperties: false,
    },
    handler: (args) => api(`/api/verified-queries/${encodeURIComponent(args.id)}/validate`, { method: 'POST' }),
  },
  {
    name: 'dataagent_search_memory',
    description: '检索长期记忆：用户偏好、业务决策、事实、SQL 模式和待确认问题。写 SQL/设计功能前先查，避免重复询问。',
    inputSchema: {
      type: 'object',
      properties: {
        query: { type: 'string' },
        scope: { type: 'string', description: 'GLOBAL / DOMAIN / DATASOURCE / USER' },
        kind: { type: 'string', description: 'PREFERENCE / FACT / DECISION / PATTERN / OPEN_QUESTION' },
      },
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: (args) => {
      const params = new URLSearchParams();
      if (args.query) params.set('q', args.query);
      if (args.scope) params.set('scope', args.scope);
      if (args.kind) params.set('kind', args.kind);
      return api(`/api/memory?${params.toString()}`);
    },
  },
  {
    name: 'dataagent_remember',
    description: '沉淀一条长期记忆（偏好/决策/事实/模式/待确认问题）；可同时提升为知识库条目。相同 scope+key 会强化而不是重复。',
    inputSchema: {
      type: 'object',
      properties: {
        scope: { type: 'string' },
        scopeKey: { type: 'string' },
        kind: { type: 'string' },
        key: { type: 'string' },
        content: { type: 'string' },
        source: { type: 'string' },
        confidence: { type: 'integer', minimum: 0, maximum: 100 },
        importance: { type: 'integer', minimum: 0, maximum: 100 },
        tags: { type: 'string' },
        promoteToKnowledge: { type: 'boolean' },
        knowledgeTitle: { type: 'string' },
        sqlText: { type: 'string' },
      },
      required: ['scope', 'kind', 'key', 'content'],
      additionalProperties: false,
    },
    handler: (args) => api('/api/memory', { method: 'POST', body: args }),
  },
  {
    name: 'dataagent_memory_overview',
    description: '查看长期记忆规模、活跃/归档数量、已提升知识数。',
    inputSchema: { type: 'object', properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: true },
    handler: () => api('/api/memory/overview'),
  },
  {
    name: 'dataagent_extract_memory_candidates',
    description: '对话结束时自动抽取长期记忆候选（偏好/决策/事实/待确认问题），进入人工审核队列，不会直接入库。',
    inputSchema: {
      type: 'object',
      properties: {
        sessionId: { type: 'string' },
        summary: { type: 'string', description: '本轮对话摘要' },
        domain: { type: 'string', description: '业务域；提供后候选归入 DOMAIN' },
        messages: {
          type: 'array',
          items: {
            type: 'object',
            properties: { role: { type: 'string' }, content: { type: 'string' } },
            required: ['content'],
            additionalProperties: false,
          },
        },
      },
      additionalProperties: false,
    },
    handler: (args) => api('/api/memory/candidates/extract', { method: 'POST', body: args }),
  },
  {
    name: 'dataagent_review_memory_candidate',
    description: '人工审核记忆候选：approve 入库（可强制覆盖冲突），reject 拒绝。',
    inputSchema: {
      type: 'object',
      properties: {
        candidateId: { type: 'string' },
        action: { type: 'string', enum: ['approve', 'reject'] },
        force: { type: 'boolean' },
        promoteToKnowledge: { type: 'boolean' },
        knowledgeTitle: { type: 'string' },
        sqlText: { type: 'string' },
        reviewedBy: { type: 'string' },
        reason: { type: 'string' },
      },
      required: ['candidateId', 'action'],
      additionalProperties: false,
    },
    handler: (args) => {
      const id = encodeURIComponent(args.candidateId);
      if (args.action === 'reject') {
        return api(`/api/memory/candidates/${id}/reject`, {
          method: 'POST',
          body: { reviewedBy: args.reviewedBy, reason: args.reason },
        });
      }
      return api(`/api/memory/candidates/${id}/approve`, {
        method: 'POST',
        body: {
          force: args.force,
          promoteToKnowledge: args.promoteToKnowledge,
          knowledgeTitle: args.knowledgeTitle,
          sqlText: args.sqlText,
          reviewedBy: args.reviewedBy,
        },
      });
    },
  },
  {
    name: 'dataagent_submit_feedback',
    description: '用户纠错/反馈闭环：一键沉淀为长期记忆，并在提供 correctedSql 时创建 DRAFT verified query。',
    inputSchema: {
      type: 'object',
      properties: {
        sessionId: { type: 'string' },
        question: { type: 'string' },
        originalSql: { type: 'string' },
        correctedSql: { type: 'string' },
        correction: { type: 'string' },
        rating: { type: 'integer', minimum: 1, maximum: 5 },
        notes: { type: 'string' },
        datasourceId: { type: 'string' },
        metricId: { type: 'string' },
        entityId: { type: 'string' },
        domain: { type: 'string' },
        reviewer: { type: 'string' },
      },
      required: ['question', 'correction'],
      additionalProperties: false,
    },
    handler: (args) => api('/api/feedback', { method: 'POST', body: args }),
  },
  {
    name: 'dataagent_audit_overview',
    description: '查看最近 24 小时审计概览：操作数、失败数、SQL 拦截数、Top 动作。',
    inputSchema: { type: 'object', properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: true },
    handler: () => api('/api/audit/overview'),
  },
  {
    name: 'dataagent_audit_search',
    description: '检索不可变审计事件，用于合规排查：谁在何时对哪个数据源执行了什么操作。',
    inputSchema: {
      type: 'object',
      properties: {
        actor: { type: 'string' },
        action: { type: 'string' },
        status: { type: 'string' },
        limit: { type: 'integer', minimum: 1, maximum: 500 },
      },
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: (args) => {
      const params = new URLSearchParams();
      ['actor', 'action', 'status', 'limit'].forEach((key) => {
        if (args[key] != null) params.set(key, String(args[key]));
      });
      return api(`/api/audit?${params.toString()}`);
    },
  },
  {
    name: 'dataagent_lineage_impact',
    description: '查看某张物理表的影响面：关联 SQL 资产、Verified Query 和语义实体；用于变更前影响分析。',
    inputSchema: {
      type: 'object',
      properties: { table: { type: 'string' } },
      required: ['table'],
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: (args) => api(`/api/lineage/impact?table=${encodeURIComponent(args.table)}`),
  },
  {
    name: 'dataagent_lineage_fields',
    description: '查看字段级血缘：表、字段、PII 类型、关联 SQL 资产与 Verified Query 计数。',
    inputSchema: { type: 'object', properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: true },
    handler: () => api('/api/lineage/fields'),
  },
  {
    name: 'dataagent_pii_classify',
    description: '判断字段是否属于 PII（邮箱/手机/身份证/银行卡/姓名/地址）并返回脱敏示例。',
    inputSchema: {
      type: 'object',
      properties: {
        column: { type: 'string' },
        label: { type: 'string' },
      },
      required: ['column'],
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: (args) => {
      const params = new URLSearchParams({ column: args.column });
      if (args.label) params.set('label', args.label);
      return api(`/api/pii/classify?${params.toString()}`);
    },
  },
  {
    name: 'dataagent_etl_overview',
    description: '查看 ETL workflow/task 规模、调度覆盖率、依赖环与孤立任务。',
    inputSchema: { type: 'object', properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: true },
    handler: () => api('/api/etl/overview'),
  },
  {
    name: 'dataagent_etl_workflows',
    description: '列出 ETL workflow：调度 cron、任务数、上下游 workflow 依赖。',
    inputSchema: { type: 'object', properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: true },
    handler: () => api('/api/etl/workflows'),
  },
  {
    name: 'dataagent_etl_analysis',
    description: '查看单个 ETL workflow 的端到端分析：拓扑顺序、关键路径、源/目标表、上下游 workflow。',
    inputSchema: {
      type: 'object',
      properties: { workflowId: { type: 'string' } },
      required: ['workflowId'],
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: (args) => api(`/api/etl/workflows/${encodeURIComponent(args.workflowId)}/analysis`),
  },
  {
    name: 'dataagent_etl_lineage',
    description: '追踪物理表在 ETL 任务中的上下游血缘，包括任务、workflow 与调度信息。',
    inputSchema: {
      type: 'object',
      properties: {
        table: { type: 'string' },
        direction: { type: 'string', enum: ['upstream', 'downstream'] },
        depth: { type: 'integer', minimum: 1, maximum: 30 },
      },
      required: ['table'],
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: (args) => {
      const params = new URLSearchParams({ table: args.table });
      if (args.direction) params.set('direction', args.direction);
      if (args.depth) params.set('depth', String(args.depth));
      return api(`/api/etl/lineage?${params.toString()}`);
    },
  },
  {
    name: 'dataagent_etl_sync_dolphinscheduler',
    description: '导入 DolphinScheduler process definition/task relation/schedule 数据，建立 ETL 任务和 workflow 依赖拓扑。',
    inputSchema: {
      type: 'object',
      properties: {
        tenantId: { type: 'string' },
        projectCode: { type: 'integer' },
        projectName: { type: 'string' },
        processDefinitions: { type: 'array', items: { type: 'object' } },
      },
      required: ['processDefinitions'],
      additionalProperties: false,
    },
    handler: (args) => api('/api/etl/sync/dolphinscheduler', { method: 'POST', body: args }),
  },
  {
    name: 'dataagent_etl_field_lineage',
    description: '查看 ETL 字段级映射：源表/字段、目标表/字段、表达式和所属任务。',
    inputSchema: {
      type: 'object',
      properties: {
        table: { type: 'string' },
        column: { type: 'string' },
      },
      required: ['table'],
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: (args) => {
      const params = new URLSearchParams({ table: args.table });
      if (args.column) params.set('column', args.column);
      return api(`/api/etl/field-lineage?${params.toString()}`);
    },
  },
  {
    name: 'dataagent_openlineage_events',
    description: '查看已采集的 OpenLineage 事件，可按 jobName 过滤。',
    inputSchema: {
      type: 'object',
      properties: { jobName: { type: 'string' } },
      additionalProperties: false,
    },
    annotations: { readOnlyHint: true },
    handler: (args) => {
      const params = new URLSearchParams();
      if (args.jobName) params.set('jobName', args.jobName);
      return api(`/api/openlineage/events?${params.toString()}`);
    },
  },
  {
    name: 'dataagent_openlineage_jobs',
    description: '查看 OpenLineage job 列表与最近事件时间。',
    inputSchema: { type: 'object', properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: true },
    handler: () => api('/api/openlineage/jobs'),
  },
  {
    name: 'dataagent_ingest_openlineage',
    description: '接收标准 OpenLineage RunEvent（Marquez 兼容 /api/v1/lineage），自动维护表级与字段级血缘。',
    inputSchema: {
      type: 'object',
      properties: { event: { type: 'object' } },
      required: ['event'],
      additionalProperties: false,
    },
    handler: (args) => api('/api/v1/lineage', { method: 'POST', body: args.event }),
  },
];

async function firstDatasourceId() {
  const payload = await api('/api/datasources');
  const first = payload?.items?.[0];
  if (!first) throw new Error('云端还没有注册任何数据源，请先在 DataAgent Studio 中新增连接');
  return first.id;
}

async function firstSchema(datasourceId) {
  const payload = await api(`/api/datasources/${datasourceId}/schemas`);
  const first = payload?.schemas?.[0];
  if (!first) throw new Error('无法读取数据源 schema');
  return first;
}

function toolByName(name) {
  return tools.find((tool) => tool.name === name);
}

async function callTool(name, args = {}) {
  const tool = toolByName(name);
  if (!tool) throw new Error(`未知工具: ${name}`);
  for (const key of tool.inputSchema.required || []) {
    if (args[key] === undefined || args[key] === null || args[key] === '') {
      throw new Error(`缺少参数: ${key}`);
    }
  }
  return tool.handler(args);
}

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });

rl.on('line', async (line) => {
  const text = line.trim();
  if (!text) return;
  let message;
  try {
    message = JSON.parse(text);
  } catch (error) {
    log('invalid JSON:', error.message);
    fail(null, -32700, 'Parse error');
    return;
  }

  const { id, method, params } = message;
  if (method === undefined) return;
  if (id === undefined) {
    // notifications/initialized and similar fire-and-forget messages
    return;
  }

  try {
    if (method === 'initialize') {
      ok(id, {
        protocolVersion: params?.protocolVersion || PROTOCOL_VERSION,
        capabilities: { tools: { listChanged: false } },
        serverInfo: SERVER_INFO,
      });
      return;
    }
    if (method === 'ping') {
      ok(id, {});
      return;
    }
    if (method === 'tools/list') {
      ok(id, {
        tools: tools.map(({ name, description, inputSchema, annotations }) => ({
          name, description, inputSchema, annotations,
        })),
      });
      return;
    }
    if (method === 'tools/call') {
      const name = params?.name;
      const args = params?.arguments || {};
      try {
        const result = await callTool(name, args);
        ok(id, {
          content: [{ type: 'text', text: JSON.stringify(result, null, 2) }],
          isError: false,
        });
      } catch (error) {
        const detail = error.payload ? ` (${error.status}) ${JSON.stringify(error.payload)}` : '';
        log('tool failed', name, error.message, detail);
        ok(id, {
          content: [{ type: 'text', text: `DataAgent 工具执行失败: ${error.message}${detail}` }],
          isError: true,
        });
      }
      return;
    }
    if (method === 'resources/list') {
      ok(id, { resources: [] });
      return;
    }
    if (method === 'prompts/list') {
      ok(id, { prompts: [] });
      return;
    }
    fail(id, -32601, `Method not found: ${method}`);
  } catch (error) {
    log('request failed', method, error.stack || error.message);
    fail(id, -32603, error.message || 'Internal error');
  }
});

rl.on('close', () => {
  process.exit(0);
});

log(`ready; cloud=${BASE_URL}; tools=${tools.length}`);
