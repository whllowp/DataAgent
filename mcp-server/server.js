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
