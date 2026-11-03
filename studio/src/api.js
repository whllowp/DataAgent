export class ApiError extends Error {
  constructor(status, code, message, path) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.path = path;
  }
}

export const DEFAULT_API_BASE = 'http://127.0.0.1:8080';
export const DEFAULT_TOKEN = 'dataagent-dev-token';

export function storageGet(key, fallback) {
  try {
    const value = window.localStorage.getItem(key);
    return value == null || value === '' ? fallback : value;
  } catch {
    return fallback;
  }
}

export function storageSet(key, value) {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // ignore private-mode storage failures
  }
}

function normalizeBase(baseUrl) {
  return (baseUrl || DEFAULT_API_BASE).trim().replace(/\/+$/, '');
}

function encodeQuery(params = {}) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : '';
}

export function createApi({ baseUrl, token }) {
  const base = normalizeBase(baseUrl);

  async function request(path, options = {}) {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), options.timeout ?? 90_000);
    try {
      const response = await fetch(base + path, {
        method: options.method || 'GET',
        headers: {
          'Content-Type': 'application/json',
          'X-API-Key': token || DEFAULT_TOKEN,
          ...(options.headers || {}),
        },
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
        signal: controller.signal,
      });
      const text = await response.text();
      let payload = null;
      if (text) {
        try {
          payload = JSON.parse(text);
        } catch {
          payload = text;
        }
      }
      if (!response.ok) {
        const message = payload && payload.message ? payload.message : `HTTP ${response.status}`;
        throw new ApiError(response.status, payload?.code || 'HTTP_ERROR', message, path);
      }
      return payload;
    } catch (error) {
      if (error.name === 'AbortError') {
        throw new ApiError(0, 'TIMEOUT', '请求超时，请检查云服务是否可达。', path);
      }
      if (error instanceof ApiError) throw error;
      throw new ApiError(0, 'NETWORK_ERROR', `无法连接云服务：${error.message}. 请在设置中确认 API 地址。`, path);
    } finally {
      window.clearTimeout(timeout);
    }
  }

  return {
    baseUrl: base,
    health: () => request('/api/health', { timeout: 5000 }),
    datasources: () => request('/api/datasources'),
    createDatasource: (body) => request('/api/datasources', { method: 'POST', body }),
    testDatasource: (id) => request(`/api/datasources/${id}/test`, { method: 'POST' }),
    schemas: (id) => request(`/api/datasources/${id}/schemas`),
    tables: (id, schema, query) => request(`/api/datasources/${id}/tables${encodeQuery({ schema, q: query })}`),
    table: (id, schema, table) =>
      request(`/api/datasources/${id}/tables/${encodeURIComponent(schema)}/${encodeURIComponent(table)}`),
    execute: (body) => request('/api/sql/execute', { method: 'POST', body }),
    explain: (body) => request('/api/sql/explain', { method: 'POST', body }),
    history: (params) => request(`/api/history${encodeQuery(params)}`),
    historyDetail: (id) => request(`/api/history/${id}`),
    solve: (id, body) => request(`/api/history/${id}/solve`, { method: 'PATCH', body }),
    note: (id, body) => request(`/api/history/${id}/note`, { method: 'PATCH', body }),
    knowledge: (params) => request(`/api/knowledge${encodeQuery(params)}`),
    knowledgeDetail: (id) => request(`/api/knowledge/${id}`),
    useKnowledge: (id) => request(`/api/knowledge/${id}/use`, { method: 'POST' }),
    createKnowledge: (body) => request('/api/knowledge', { method: 'POST', body }),
    sqlAssets: (params) => request(`/api/sql-assets${encodeQuery(params)}`),
    sqlAssetStats: () => request('/api/sql-assets/stats'),
    saveSqlAsset: (body) => request('/api/sql-assets', { method: 'POST', body }),
    importSqlAssets: (items) => request('/api/sql-assets/import', { method: 'POST', body: { items } }),
    semanticOverview: () => request('/api/semantic/overview'),
    semanticEntities: (q) => request(`/api/semantic/entities${encodeQuery({ q })}`),
    semanticMetrics: (q) => request(`/api/semantic/metrics${encodeQuery({ q })}`),
    semanticTerms: (q) => request(`/api/semantic/terms${encodeQuery({ q })}`),
    semanticRelationships: (q) => request(`/api/semantic/relationships${encodeQuery({ q })}`),
    semanticContext: (q) => request(`/api/semantic/context${encodeQuery({ q })}`),
    semanticDesign: (body) => request('/api/semantic/design', { method: 'POST', body }),
    semanticSync: () => request('/api/semantic/sync', { method: 'POST' }),
    semanticSyncRuns: () => request('/api/semantic/sync/runs'),
    verifiedQueries: (q) => request(`/api/verified-queries${encodeQuery({ q })}`),
    validateVerifiedQuery: (id) => request(`/api/verified-queries/${id}/validate`, { method: 'POST' }),
    useVerifiedQuery: (id) => request(`/api/verified-queries/${id}/use`, { method: 'POST' }),
    memories: (q) => request(`/api/memory${encodeQuery({ q })}`),
    archiveMemory: (id) => request(`/api/memory/${id}/archive`, { method: 'POST' }),
    opsOverview: () => request('/api/ops/overview'),
    benchmarks: () => request('/api/benchmarks'),
    runBenchmark: (id) => request(`/api/benchmarks/${id}/run`, { method: 'POST' }),
    runAllBenchmarks: () => request('/api/benchmarks/run', { method: 'POST' }),
    analyze: (body) => request('/api/optimize/analyze', { method: 'POST', body }),
    compare: (body) => request('/api/optimize/compare', { method: 'POST', body }),
    prompt: (body) => request('/api/optimize/prompt', { method: 'POST', body }),
    stats: () => request('/api/stats/overview'),
  };
}
