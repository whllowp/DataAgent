import React from 'react';
import { createApi, DEFAULT_API_BASE, DEFAULT_TOKEN, storageGet, storageSet } from './api.js';
import { css } from './styles.js';
import { Icon, DataAgentIcon } from './icons.jsx';

const DEFAULT_SQL = `SELECT c.city,
       COUNT(*) AS orders,
       SUM(o.amount) AS gmv
FROM sales_orders o
JOIN customers c ON c.id = o.customer_id
WHERE o.order_date >= '2025-01-01'
  AND o.order_date < '2026-01-01'
GROUP BY c.city
ORDER BY gmv DESC`;

const FORMAT_KEYWORDS = [
  'LEFT OUTER JOIN', 'RIGHT OUTER JOIN', 'FULL OUTER JOIN', 'LEFT JOIN', 'RIGHT JOIN',
  'INNER JOIN', 'CROSS JOIN', 'GROUP BY', 'ORDER BY', 'UNION ALL', 'UNION', 'FROM', 'WHERE',
  'HAVING', 'LIMIT', 'OFFSET', 'JOIN', 'ON', 'WITH', 'SELECT', 'INSERT', 'UPDATE', 'DELETE',
];

function uid() {
  return `t_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

function formatTime(value) {
  if (!value) return '';
  try {
    return new Date(value).toLocaleString('zh-CN', { hour12: false });
  } catch {
    return value;
  }
}

function formatDuration(ms) {
  if (ms == null) return '—';
  if (ms < 1000) return `${ms} ms`;
  return `${(ms / 1000).toFixed(2)} s`;
}

function formatBytes(value) {
  if (value == null) return '—';
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function quoteIdent(name) {
  return name && /^[A-Za-z_][A-Za-z0-9_$]*$/.test(name) ? name : `"${String(name || '').replaceAll('"', '""')}"`;
}

function formatSql(sql) {
  if (!sql || !sql.trim()) return sql;
  let out = '';
  let i = 0;
  let depth = 0;
  let quote = null;
  const upper = sql.toUpperCase();
  while (i < sql.length) {
    const ch = sql[i];
    if (quote) {
      out += ch;
      if (ch === quote) {
        if (sql[i + 1] === quote) {
          out += sql[i + 1];
          i += 2;
          continue;
        }
        quote = null;
      }
      i++;
      continue;
    }
    if (ch === "'" || ch === '"' || ch === '`') {
      quote = ch;
      out += ch;
      i++;
      continue;
    }
    if (ch === '-' && sql[i + 1] === '-') {
      const end = sql.indexOf('\n', i);
      const comment = end === -1 ? sql.slice(i) : sql.slice(i, end);
      out += comment + (end === -1 ? '' : '\n');
      i = end === -1 ? sql.length : end + 1;
      continue;
    }
    if (ch === '(') {
      depth++;
      out += ch;
      i++;
      continue;
    }
    if (ch === ')') {
      depth = Math.max(0, depth - 1);
      out += ch;
      i++;
      continue;
    }
    if (/\s/.test(ch)) {
      if (!out.endsWith(' ') && !out.endsWith('\n')) out += ' ';
      i++;
      continue;
    }
    if (depth === 0) {
      let matched = null;
      for (const keyword of FORMAT_KEYWORDS) {
        if (upper.startsWith(keyword, i)) {
          const before = i === 0 ? ' ' : sql[i - 1];
          const after = sql[i + keyword.length] || ' ';
          if (!/[A-Za-z0-9_$]/.test(before) && !/[A-Za-z0-9_$]/.test(after)) {
            matched = keyword;
            break;
          }
        }
      }
      if (matched) {
        const trimmed = out.replace(/[ \t]+$/, '');
        if (trimmed && !trimmed.endsWith('\n')) out = `${trimmed}\n`;
        out += matched;
        i += matched.length;
        continue;
      }
    }
    out += ch;
    i++;
  }
  return out
    .split('\n')
    .map((line) => line.replace(/[ \t]+/g, ' ').trimEnd())
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

async function copyText(text) {
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const area = document.createElement('textarea');
  area.value = text;
  area.style.position = 'fixed';
  area.style.opacity = '0';
  document.body.appendChild(area);
  area.select();
  document.execCommand('copy');
  area.remove();
}

function csvEscape(value) {
  if (value === null || value === undefined) return '';
  const text = typeof value === 'object' ? JSON.stringify(value) : String(value);
  return `"${text.replaceAll('"', '""')}"`;
}

function downloadCsv(result, datasourceName) {
  if (!result || !result.columns || !result.rows) return;
  const lines = [
    result.columns.map((column) => csvEscape(column.label || column.name)).join(','),
    ...result.rows.map((row) => row.map(csvEscape).join(',')),
  ];
  const blob = new Blob([`\ufeff${lines.join('\r\n')}`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `dataagent-${(datasourceName || 'result').replace(/[^\w\u4e00-\u9fa5-]+/g, '_')}-${Date.now()}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}

function parseTags(tags) {
  if (!tags) return [];
  return String(tags).split(/[,，]/).map((tag) => tag.trim()).filter(Boolean);
}

function severityLabel(level) {
  return { HIGH: '高', MEDIUM: '中', LOW: '低', INFO: '提示' }[String(level || '').toUpperCase()] || level;
}

function verdictLabel(verdict) {
  return {
    FASTER: '候选更快',
    SLOWER: '候选更慢',
    SIMILAR: '差异不显著',
    ORIGINAL_FAILED: '原 SQL 失败',
    CANDIDATE_FAILED: '候选 SQL 失败',
    PLAN_ONLY: '仅计划对比',
  }[verdict] || verdict;
}

function nextTabTitle(tabs) {
  const numbers = tabs
    .map((tab) => /^查询 (\d+)$/.exec(tab.title))
    .filter(Boolean)
    .map((match) => Number(match[1]));
  return `查询 ${numbers.length ? Math.max(...numbers) + 1 : 1}`;
}

function normalizeSqlValue(value) {
  if (value === null || value === undefined) return '';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

export function SqlStudio({ embedded = false }) {
  const [theme, setTheme] = React.useState(() =>
    storageGet('dataagent.theme', embedded
      ? (document.body.hasAttribute('data-ds-dark-theme') ? 'dark' : 'light')
      : 'dark'),
  );
  const [apiBase, setApiBase] = React.useState(() => storageGet('dataagent.apiBase', DEFAULT_API_BASE));
  const [token, setToken] = React.useState(() => storageGet('dataagent.token', DEFAULT_TOKEN));
  const [settingsOpen, setSettingsOpen] = React.useState(false);
  const [connectionOpen, setConnectionOpen] = React.useState(false);
  const api = React.useMemo(() => createApi({ baseUrl: apiBase, token }), [apiBase, token]);

  const [datasources, setDatasources] = React.useState([]);
  const [activeDsId, setActiveDsId] = React.useState(() => storageGet('dataagent.datasource', ''));
  const [loadingDs, setLoadingDs] = React.useState(true);
  const [connError, setConnError] = React.useState('');

  const [schemas, setSchemas] = React.useState([]);
  const [schema, setSchema] = React.useState('');
  const [metaVersion, setMetaVersion] = React.useState(0);
  const [tables, setTables] = React.useState([]);
  const [tablesLoading, setTablesLoading] = React.useState(false);
  const [tableFilter, setTableFilter] = React.useState('');
  const [tableDetails, setTableDetails] = React.useState({});
  const [expandedTable, setExpandedTable] = React.useState('');
  const [metaError, setMetaError] = React.useState('');

  const [tabs, setTabs] = React.useState([{ id: uid(), title: '查询 1', sql: DEFAULT_SQL, dirty: false }]);
  const [activeTabId, setActiveTabId] = React.useState(() => tabs[0]?.id);
  const editorRef = React.useRef(null);
  const gutterRef = React.useRef(null);

  const [busy, setBusy] = React.useState('');
  const [result, setResult] = React.useState(null);
  const [plan, setPlan] = React.useState(null);
  const [resultTab, setResultTab] = React.useState('data');
  const [message, setMessage] = React.useState('');
  const [analysis, setAnalysis] = React.useState(null);
  const [comparison, setComparison] = React.useState(null);
  const [candidateSql, setCandidateSql] = React.useState('');
  const [rightTab, setRightTab] = React.useState('assistant');
  const [rightOpen, setRightOpen] = React.useState(() => (typeof window === 'undefined' ? true : window.innerWidth > 1180));

  const [history, setHistory] = React.useState([]);
  const [historyLoading, setHistoryLoading] = React.useState(false);
  const [historyQuery, setHistoryQuery] = React.useState('');
  const [knowledge, setKnowledge] = React.useState([]);
  const [knowledgeLoading, setKnowledgeLoading] = React.useState(false);
  const [knowledgeQuery, setKnowledgeQuery] = React.useState('');
  const [openKbId, setOpenKbId] = React.useState('');
  const [stats, setStats] = React.useState(null);

  const [solveTarget, setSolveTarget] = React.useState(null);
  const [toast, setToast] = React.useState(null);

  const activeDs = datasources.find((item) => item.id === activeDsId) || null;
  const activeTab = tabs.find((tab) => tab.id === activeTabId) || tabs[0];
  const filteredTables = React.useMemo(() => {
    const query = tableFilter.trim().toLowerCase();
    if (!query) return tables;
    return tables.filter((table) => table.name.toLowerCase().includes(query));
  }, [tables, tableFilter]);
  const tableCount = tables.length;

  function showToast(msg, type = 'info') {
    setToast({ id: uid(), message: msg, type });
  }

  React.useEffect(() => {
    if (!toast) return undefined;
    const timer = window.setTimeout(() => setToast(null), 4200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  React.useEffect(() => {
    document.body.classList.toggle('da-standalone', !embedded);
    return () => document.body.classList.remove('da-standalone');
  }, [embedded]);

  React.useEffect(() => {
    storageSet('dataagent.theme', theme);
  }, [theme]);

  React.useEffect(() => {
    setResult(null);
    setPlan(null);
    setMessage('');
    setAnalysis(null);
    setComparison(null);
    setCandidateSql('');
    setResultTab('data');
  }, [activeTabId, activeDsId]);

  function openRight(tab) {
    setRightTab(tab);
    setRightOpen(true);
  }

  async function loadDatasources() {
    setLoadingDs(true);
    setConnError('');
    try {
      const payload = await api.datasources();
      const items = payload?.items || [];
      setDatasources(items);
      setActiveDsId((current) => {
        if (items.some((item) => item.id === current)) return current;
        return items[0]?.id || '';
      });
    } catch (error) {
      setConnError(error.message);
      setDatasources([]);
      setActiveDsId('');
    } finally {
      setLoadingDs(false);
    }
  }

  React.useEffect(() => {
    loadDatasources();
  }, [api]);

  React.useEffect(() => {
    if (activeDsId) storageSet('dataagent.datasource', activeDsId);
  }, [activeDsId]);

  React.useEffect(() => {
    if (!activeDsId) {
      setSchemas([]);
      setSchema('');
      return undefined;
    }
    let cancelled = false;
    setSchemas([]);
    setSchema('');
    setTables([]);
    setTableDetails({});
    setExpandedTable('');
    api.schemas(activeDsId)
      .then((payload) => {
        if (cancelled) return;
        const values = payload?.schemas || [];
        setSchemas(values);
        setSchema((current) => (values.includes(current) ? current : values[0] || ''));
      })
      .catch((error) => {
        if (!cancelled) setMetaError(error.message);
      });
    return () => {
      cancelled = true;
    };
  }, [activeDsId, api, metaVersion]);

  React.useEffect(() => {
    if (!activeDsId || !schema) {
      setTables([]);
      return undefined;
    }
    let cancelled = false;
    setTablesLoading(true);
    setMetaError('');
    api.tables(activeDsId, schema)
      .then((payload) => {
        if (!cancelled) setTables(payload?.tables || []);
      })
      .catch((error) => {
        if (!cancelled) {
          setTables([]);
          setMetaError(error.message);
        }
      })
      .finally(() => {
        if (!cancelled) setTablesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [activeDsId, schema, api, metaVersion]);

  React.useEffect(() => {
    loadStats();
  }, [api]);

  React.useEffect(() => {
    if (rightTab === 'history') loadHistory();
    if (rightTab === 'knowledge') loadKnowledge();
  }, [rightTab, rightOpen, api]);

  async function loadStats() {
    try {
      setStats(await api.stats());
    } catch {
      setStats(null);
    }
  }

  async function loadHistory(query = historyQuery) {
    setHistoryLoading(true);
    try {
      const payload = await api.history({ size: 30, q: query });
      setHistory((payload?.items || []).filter(
        (item) => item.userId !== 'benchmark' && item.userId !== 'optimizer',
      ));
    } catch (error) {
      showToast(error.message, 'error');
    } finally {
      setHistoryLoading(false);
    }
  }

  async function loadKnowledge(query = knowledgeQuery) {
    setKnowledgeLoading(true);
    try {
      const payload = await api.knowledge({ size: 30, q: query });
      setKnowledge(payload?.items || []);
    } catch (error) {
      showToast(error.message, 'error');
    } finally {
      setKnowledgeLoading(false);
    }
  }

  function updateTabSql(value) {
    setTabs((current) => current.map((tab) => (
      tab.id === activeTabId ? { ...tab, sql: value, dirty: true } : tab
    )));
  }

  function addTab(sql = DEFAULT_SQL, title = null) {
    if (tabs.length >= 8) {
      showToast('最多同时打开 8 个查询页。', 'error');
      return;
    }
    const tab = { id: uid(), title: title || nextTabTitle(tabs), sql, dirty: false };
    setTabs((current) => [...current, tab]);
    setActiveTabId(tab.id);
  }

  function openSql(sql, title = '历史 SQL') {
    if (!sql) return;
    if (tabs.length >= 8) {
      showToast('最多同时打开 8 个查询页。', 'error');
      return;
    }
    const tab = { id: uid(), title: title.length > 24 ? `${title.slice(0, 24)}…` : title, sql, dirty: false };
    setTabs((current) => [...current, tab]);
    setActiveTabId(tab.id);
    showToast('已在新查询页打开。');
  }

  function closeTab(tabId) {
    setTabs((current) => {
      const index = current.findIndex((tab) => tab.id === tabId);
      if (index < 0) return current;
      const next = current.filter((tab) => tab.id !== tabId);
      if (!next.length) {
        const fresh = { id: uid(), title: '查询 1', sql: DEFAULT_SQL, dirty: false };
        setActiveTabId(fresh.id);
        return [fresh];
      }
      if (tabId === activeTabId) {
        setActiveTabId(next[Math.max(0, index - 1)].id);
      }
      return next;
    });
  }

  async function ensureReady() {
    if (!activeDsId) {
      showToast('请先选择一个数据源。', 'error');
      return false;
    }
    if (!activeTab?.sql?.trim()) {
      showToast('SQL 不能为空。', 'error');
      return false;
    }
    return true;
  }

  async function runSql() {
    if (!(await ensureReady()) || busy) return;
    setBusy('execute');
    setMessage('');
    setResultTab('data');
    try {
      const payload = await api.execute({
        datasourceId: activeDsId,
        sql: activeTab.sql,
        maxRows: 500,
        includePlan: true,
      });
      setResult(payload);
      setPlan(payload.plan || null);
      if (payload.status === 'ERROR') {
        setMessage(payload.message || 'SQL 执行失败');
        setResultTab('messages');
      }
      setTabs((current) => current.map((tab) => (
        tab.id === activeTabId ? { ...tab, dirty: false } : tab
      )));
      loadStats();
      if (rightTab === 'history') loadHistory();
    } catch (error) {
      setMessage(error.message);
      setResultTab('messages');
      showToast(error.message, 'error');
    } finally {
      setBusy('');
    }
  }

  async function explainSql() {
    if (!(await ensureReady()) || busy) return;
    setBusy('explain');
    setMessage('');
    setResult(null);
    try {
      const payload = await api.explain({ datasourceId: activeDsId, sql: activeTab.sql });
      setPlan(payload.plan);
      setResultTab('plan');
      loadStats();
    } catch (error) {
      setMessage(error.message);
      setResultTab('messages');
      showToast(error.message, 'error');
    } finally {
      setBusy('');
    }
  }

  async function analyzeSql() {
    if (!(await ensureReady()) || busy) return;
    setBusy('analyze');
    setMessage('');
    setRightTab('assistant');
    setRightOpen(true);    try {
      const payload = await api.analyze({
        datasourceId: activeDsId,
        sql: activeTab.sql,
        includePlan: true,
      });
      setAnalysis(payload);
      if (payload.plan) {
        setPlan(payload.plan);
        setResultTab('plan');
      }
      if (!candidateSql) setCandidateSql('');
      showToast('分析完成，已结合执行计划给出检查项。', 'success');
    } catch (error) {
      showToast(error.message, 'error');
    } finally {
      setBusy('');
    }
  }

  async function compareSql() {
    if (!candidateSql.trim()) {
      showToast('请先填写候选 SQL。', 'error');
      return;
    }
    if (busy) return;
    setBusy('compare');
    try {
      const payload = await api.compare({
        datasourceId: activeDsId,
        originalSql: activeTab.sql,
        candidateSql,
        runs: 2,
        execute: true,
        notes: 'DataAgent Studio 对比',
      });
      setComparison(payload);
      setRightTab('assistant');
      setRightOpen(true);
      showToast(`对比完成：${verdictLabel(payload.verdict)}`, payload.verdict === 'FASTER' ? 'success' : 'info');
      loadStats();
      if (rightTab === 'history') loadHistory();
    } catch (error) {
      showToast(error.message, 'error');
    } finally {
      setBusy('');
    }
  }

  function applyFormat() {
    updateTabSql(formatSql(activeTab.sql));
    showToast('已格式化当前 SQL。');
  }

  async function handleCopy(text, label = '已复制') {
    try {
      await copyText(text);
      showToast(label, 'success');
    } catch (error) {
      showToast(`复制失败：${error.message}`, 'error');
    }
  }

  async function toggleTable(table) {
    const key = `${table.schema}.${table.name}`;
    if (expandedTable === key) {
      setExpandedTable('');
      return;
    }
    setExpandedTable(key);
    if (!tableDetails[key]) {
      setTableDetails((current) => ({ ...current, [key]: { loading: true } }));
      try {
        const detail = await api.table(activeDsId, table.schema || schema, table.name);
        setTableDetails((current) => ({ ...current, [key]: { detail } }));
      } catch (error) {
        setTableDetails((current) => ({ ...current, [key]: { error: error.message } }));
      }
    }
  }

  function insertText(text) {
    if (!activeTab) return;
    const editor = editorRef.current;
    if (!editor) {
      updateTabSql(`${activeTab.sql}${activeTab.sql.endsWith(' ') ? '' : ' '}${text}`);
      return;
    }
    const start = editor.selectionStart ?? activeTab.sql.length;
    const end = editor.selectionEnd ?? start;
    const next = `${activeTab.sql.slice(0, start)}${text}${activeTab.sql.slice(end)}`;
    updateTabSql(next);
    window.requestAnimationFrame(() => {
      editor.focus();
      const position = start + text.length;
      editor.setSelectionRange(position, position);
    });
  }

  function insertTable(table) {
    const full = `${table.schema ? `${quoteIdent(table.schema)}.` : ''}${quoteIdent(table.name)}`;
    insertText(full);
  }

  function openSolve(item) {
    setSolveTarget({
      item,
      title: item.problem || 'SQL 优化记录',
      problem: item.problem || '',
      rootCause: '',
      optimizationSteps: '',
      optimizedSql: '',
      tags: item.tags || '',
      afterDurationMs: '',
    });
  }

  async function submitSolve(form) {
    if (!solveTarget) return;
    try {
      await api.solve(solveTarget.item.id, {
        title: form.title,
        problem: form.problem,
        rootCause: form.rootCause,
        optimizationSteps: form.optimizationSteps,
        optimizedSql: form.optimizedSql,
        tags: form.tags,
        afterDurationMs: form.afterDurationMs ? Number(form.afterDurationMs) : null,
      });
      setSolveTarget(null);
      showToast('已沉淀到团队知识库。', 'success');
      loadHistory();
      loadKnowledge();
      loadStats();
    } catch (error) {
      showToast(error.message, 'error');
    }
  }

  function handleEditorKeyDown(event) {
    if (event.key === 'Tab') {
      event.preventDefault();
      insertText('  ');
      return;
    }
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      event.preventDefault();
      runSql();
    }
    if ((event.ctrlKey || event.metaKey) && event.shiftKey && event.key === 'Enter') {
      event.preventDefault();
      explainSql();
    }
  }

  function handleEditorScroll(event) {
    if (gutterRef.current) {
      gutterRef.current.scrollTop = event.currentTarget.scrollTop;
    }
  }

  const errorCount = message ? 1 : 0;
  const canRun = Boolean(activeDsId) && Boolean(activeTab?.sql?.trim());

  return (
    <div className="da-app" data-theme={theme}>
      <style>{css}</style>

      <header className="da-header" key={theme}>
        <div className="da-brand">
          <div className="da-logo"><DataAgentIcon size={20} /></div>
          <div>
            <div className="da-brand-name">DataAgent Studio</div>
            <div className="da-brand-sub">Cloud SQL Workspace</div>
          </div>
        </div>

        <div className="da-header-spacer" />

        <div className="da-ds-select" title={connError || (activeDs ? activeDs.description : '选择数据源')}>
          <span className={`da-dot ${activeDs ? '' : 'off'}`} />
          <select
            value={activeDsId}
            onChange={(event) => setActiveDsId(event.target.value)}
            aria-label="选择数据源"
          >
            {!datasources.length ? <option value="">{loadingDs ? '加载连接…' : '未配置连接'}</option> : null}
            {datasources.map((item) => (
              <option key={item.id} value={item.id}>{item.name} · {item.dialect}</option>
            ))}
          </select>
          <button className="da-icon-btn" onClick={() => setConnectionOpen(true)} title="新增云连接">
            <Icon name="plus" size={14} />
          </button>
        </div>

        <div className="da-top-actions">
          <span className="da-readonly" title="云端执行网关默认只允许只读查询">
            <Icon name="lock" size={11} /> 只读
          </span>
          <button
            className="da-icon-btn"
            onClick={() => { setRightTab('assistant'); setRightOpen((value) => !value); }}
            title="切换右侧面板"
          >
            <Icon name="panel" size={15} />
          </button>
          <button
            className="da-icon-btn"
            onClick={() => setTheme((value) => (value === 'dark' ? 'light' : 'dark'))}
            title={theme === 'dark' ? '切换到浅色主题' : '切换到深色主题'}
          >
            <Icon name={theme === 'dark' ? 'sun' : 'moon'} size={15} />
          </button>
          <button className="da-icon-btn" onClick={() => setSettingsOpen(true)} title="设置">
            <Icon name="settings" size={15} />
          </button>
        </div>
      </header>

      <div className="da-body">
        <aside className="da-left">
          <div className="da-side-head">
            <span className="da-section-label">对象浏览器</span>
            <div className="da-side-actions">
              <button
                className="da-icon-btn"
                title="刷新元数据"
                onClick={() => setMetaVersion((value) => value + 1)}
              >
                <Icon name="refresh" size={13} />
              </button>
              <button className="da-icon-btn" title="新增连接" onClick={() => setConnectionOpen(true)}>
                <Icon name="plus" size={14} />
              </button>
            </div>
          </div>

          <div className="da-search">
            <Icon name="search" size={13} />
            <input
              value={tableFilter}
              onChange={(event) => setTableFilter(event.target.value)}
              placeholder="搜索表 / 视图"
              aria-label="搜索表"
            />
            {tableFilter ? (
              <button className="da-icon-btn" style={{ height: 20, minWidth: 20, padding: 0 }} onClick={() => setTableFilter('')}>
                <Icon name="close" size={11} />
              </button>
            ) : null}
          </div>

          {schemas.length ? (
            <select className="da-schema-select" value={schema} onChange={(event) => setSchema(event.target.value)} aria-label="选择 schema">
              {schemas.map((item) => <option key={item} value={item}>{item}</option>)}
            </select>
          ) : null}

          <div className="da-tree">
            {metaError ? <div className="da-error-box">{metaError}</div> : null}
            {tablesLoading ? (
              <div className="da-tree-empty"><Icon name="refresh" size={14} className="da-spin" /> 正在读取元数据…</div>
            ) : null}
            {!tablesLoading && !filteredTables.length ? (
              <div className="da-tree-empty">
                {connError ? '云服务不可达，请检查设置。' : (tables.length ? '没有匹配的表。' : '当前 schema 没有表。')}
              </div>
            ) : null}
            {filteredTables.map((table) => {
              const key = `${table.schema}.${table.name}`;
              const open = expandedTable === key;
              const cached = tableDetails[key];
              return (
                <div className="da-table-node" key={key}>
                  <button
                    className={`da-table-row ${open ? 'open selected' : ''}`}
                    onClick={() => toggleTable(table)}
                    onDoubleClick={() => {
                      insertText(`SELECT *\nFROM ${table.schema ? `${quoteIdent(table.schema)}.` : ''}${quoteIdent(table.name)}\nLIMIT 100`);
                    }}
                    title={`${table.schema}.${table.name} · 双击插入查询`}
                  >
                    <Icon name="chevron" size={11} className="da-chevron" />
                    <Icon name="table" size={13} />
                    <span className="da-table-name">{table.name}</span>
                    <span className="da-table-kind">{table.type === 'VIEW' ? 'view' : ''}</span>
                  </button>
                  {open ? (
                    <div className="da-columns">
                      {!cached || cached.loading ? (
                        <div className="da-tree-empty" style={{ padding: '6px 4px', textAlign: 'left' }}>加载列…</div>
                      ) : null}
                      {cached?.error ? <div className="da-error-box">{cached.error}</div> : null}
                      {cached?.detail?.columns?.map((column) => (
                        <button
                          className="da-column"
                          key={column.name}
                          onClick={() => insertText(quoteIdent(column.name))}
                          title={`${column.name} · ${column.typeName} · 点击插入列名`}
                        >
                          <Icon name="column" size={10} />
                          <span className="da-col-name">{column.name}</span>
                          <span className="da-col-type">{column.typeName}</span>
                          {column.primaryKey ? <span className="da-pk">PK</span> : null}
                        </button>
                      ))}
                      {cached?.detail ? (
                        <button
                          className="da-btn small ghost"
                          style={{ margin: '4px 0 0 4px' }}
                          onClick={() => {
                            insertText(`SELECT *\nFROM ${table.schema ? `${quoteIdent(table.schema)}.` : ''}${quoteIdent(table.name)}\nLIMIT 100`);
                          }}
                        >
                          <Icon name="plus" size={10} /> 查询 100 行
                        </button>
                      ) : null}
                    </div>
                  ) : null}
                </div>
              );
            })}
          </div>

          <div className="da-left-foot">
            <span>{tableCount} 张表</span>
            <span>{activeDs?.dialect || '—'}</span>
          </div>
        </aside>

        <main className="da-center">
          <div className="da-tabs">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                className={`da-tab ${tab.id === activeTabId ? 'active' : ''}`}
                onClick={() => setActiveTabId(tab.id)}
              >
                <span className="da-tab-label">{tab.title}{tab.dirty ? ' •' : ''}</span>
                <span
                  className="da-tab-close"
                  role="button"
                  tabIndex={0}
                  onClick={(event) => { event.stopPropagation(); closeTab(tab.id); }}
                  onKeyDown={(event) => { if (event.key === 'Enter') { event.stopPropagation(); closeTab(tab.id); } }}
                  title="关闭"
                >
                  <Icon name="close" size={10} />
                </span>
              </button>
            ))}
            <button className="da-tab-add" onClick={() => addTab()} title="新建查询">
              <Icon name="plus" size={14} />
            </button>
          </div>

          <div className="da-editor-head">
            <div className="da-toolbar-group">
              <button className="da-btn primary" onClick={runSql} disabled={!canRun || busy === 'execute'}>
                <Icon name={busy === 'execute' ? 'refresh' : 'play'} size={12} className={busy === 'execute' ? 'da-spin' : ''} />
                {busy === 'execute' ? '执行中' : '运行'}
                <span className="da-kbd">Ctrl↵</span>
              </button>
              <button className="da-btn" onClick={explainSql} disabled={!canRun || Boolean(busy)} title="仅获取执行计划，不执行查询">
                <Icon name={busy === 'explain' ? 'refresh' : 'plan'} size={12} className={busy === 'explain' ? 'da-spin' : ''} />
                执行计划
              </button>
              <button className="da-btn accent" onClick={analyzeSql} disabled={!canRun || Boolean(busy)} title="静态规则 + 执行计划 + DSH 优化提示词">
                <Icon name={busy === 'analyze' ? 'refresh' : 'sparkles'} size={12} className={busy === 'analyze' ? 'da-spin' : ''} />
                AI 优化
              </button>
            </div>
            <div className="da-toolbar-sep" />
            <div className="da-toolbar-group">
              <button className="da-icon-btn" onClick={applyFormat} title="格式化 SQL">
                <Icon name="format" size={14} />
              </button>
              <button className="da-icon-btn" onClick={() => handleCopy(activeTab?.sql || '')} title="复制 SQL">
                <Icon name="copy" size={14} />
              </button>
            </div>
            <div className="da-editor-meta">
              <span>{activeTab?.sql?.split('\n').length || 0} 行</span>
              <span>{activeTab?.sql?.length || 0} 字符</span>
              <span className="da-readonly"><Icon name="lock" size={10} /> 云端只读</span>
            </div>
          </div>

          <div className="da-editor-shell">
            <div className="da-gutter" ref={gutterRef}>
              <div className="da-gutter-inner">
                {Array.from({ length: Math.max(1, (activeTab?.sql || '').split('\n').length) }, (_, index) => index + 1).join('\n')}
              </div>
            </div>
            <textarea
              ref={editorRef}
              className="da-editor"
              value={activeTab?.sql || ''}
              onChange={(event) => updateTabSql(event.target.value)}
              onKeyDown={handleEditorKeyDown}
              onScroll={handleEditorScroll}
              spellCheck={false}
              placeholder="-- 输入只读 SQL，Ctrl+Enter 运行"
              aria-label="SQL 编辑器"
            />
          </div>

          <ResultsPane
            result={result}
            plan={plan}
            resultTab={resultTab}
            setResultTab={setResultTab}
            message={message}
            errorCount={errorCount}
            busy={busy}
            datasourceName={activeDs?.name}
            onExplain={explainSql}
            onExport={() => downloadCsv(result, activeDs?.name)}
            onCopyJson={() => handleCopy(JSON.stringify(result?.rows || [], null, 2), '结果 JSON 已复制')}
            onCopyPlan={() => handleCopy(plan?.raw || '', '执行计划已复制')}
          />

          <div className="da-sidebar-footer">
            <span className={`da-status-dot ${busy ? 'busy' : activeDs ? '' : 'off'}`} />
            <span>{busy ? '正在请求云服务…' : connError ? '云服务离线' : '云服务已连接'}</span>
            <span style={{ marginLeft: 'auto' }}>{stats ? `今日 ${stats.queriesToday} 次 · P95 ${formatDuration(stats.p95DurationMs)} · 知识 ${stats.knowledgeCount} 条` : '等待统计'}</span>
          </div>
        </main>

        <aside className={`da-right ${rightOpen ? 'open' : ''}`}>
          <div className="da-right-tabs">
            <button className={`da-right-tab ${rightTab === 'assistant' ? 'active' : ''}`} onClick={() => openRight('assistant')}>
              <Icon name="sparkles" size={12} /> AI 优化
            </button>
            <button className={`da-right-tab ${rightTab === 'history' ? 'active' : ''}`} onClick={() => openRight('history')}>
              <Icon name="history" size={12} /> 历史
            </button>
            <button className={`da-right-tab ${rightTab === 'knowledge' ? 'active' : ''}`} onClick={() => openRight('knowledge')}>
              <Icon name="book" size={12} /> 知识库
            </button>
            <button className="da-icon-btn" style={{ marginLeft: 'auto' }} onClick={() => setRightOpen(false)} title="收起面板">
              <Icon name="close" size={13} />
            </button>
          </div>

          <div className="da-right-body">
            {rightTab === 'assistant' ? (
              <AssistantPanel
                analysis={analysis}
                comparison={comparison}
                candidateSql={candidateSql}
                setCandidateSql={setCandidateSql}
                onAnalyze={analyzeSql}
                onCompare={compareSql}
                onCopyPrompt={() => handleCopy(analysis?.promptContext || '', 'DSH 优化提示词已复制')}
                onCopySql={handleCopy}
                busy={busy}
                stats={stats}
              />
            ) : null}
            {rightTab === 'history' ? (
              <HistoryPanel
                history={history}
                loading={historyLoading}
                query={historyQuery}
                setQuery={setHistoryQuery}
                onSearch={() => loadHistory()}
                onRefresh={() => loadHistory()}
                onOpen={(item) => openSql(item.sqlText, item.problem || '历史 SQL')}
                onSolve={openSolve}
                stats={stats}
              />
            ) : null}
            {rightTab === 'knowledge' ? (
              <KnowledgePanel
                items={knowledge}
                loading={knowledgeLoading}
                query={knowledgeQuery}
                setQuery={setKnowledgeQuery}
                onSearch={() => loadKnowledge()}
                onRefresh={() => loadKnowledge()}
                openId={openKbId}
                setOpenId={setOpenKbId}
                onOpenSql={(sql) => openSql(sql, '知识库 SQL')}
                onUse={async (item) => {
                  try {
                    await api.useKnowledge(item.id);
                    showToast('引用次数 +1', 'success');
                    loadKnowledge();
                  } catch (error) {
                    showToast(error.message, 'error');
                  }
                }}
                onCopy={handleCopy}
              />
            ) : null}
          </div>
        </aside>
      </div>

      {settingsOpen ? (
        <SettingsModal
          apiBase={apiBase}
          token={token}
          theme={theme}
          onTheme={setTheme}
          onClose={() => setSettingsOpen(false)}
          onSave={(nextBase, nextToken) => {
            setApiBase(nextBase);
            setToken(nextToken);
            setSettingsOpen(false);
            showToast('设置已保存，正在重新连接云服务。', 'success');
          }}
        />
      ) : null}

      {connectionOpen ? (
        <ConnectionModal
          api={api}
          onClose={() => setConnectionOpen(false)}
          onCreated={async (created) => {
            setConnectionOpen(false);
            await loadDatasources();
            if (created?.id) setActiveDsId(created.id);
            showToast('数据源已注册到云端。', 'success');
          }}
        />
      ) : null}

      {solveTarget ? (
        <SolveModal
          target={solveTarget}
          onClose={() => setSolveTarget(null)}
          onSubmit={submitSolve}
          onCopy={handleCopy}
        />
      ) : null}

      {toast ? <div className={`da-toast ${toast.type}`}>{toast.message}</div> : null}
    </div>
  );
}

function ResultsPane({
  result, plan, resultTab, setResultTab, message, errorCount, busy,
  datasourceName, onExplain, onExport, onCopyJson, onCopyPlan,
}) {
  const hasResult = Boolean(result);
  const columns = result?.columns || [];
  return (
    <section className="da-results">
      <div className="da-result-tabs">
        <button className={`da-rtab ${resultTab === 'data' ? 'active' : ''}`} onClick={() => setResultTab('data')}>
          数据 {result ? <span className="da-count">{result.rowCount}</span> : null}
        </button>
        <button className={`da-rtab ${resultTab === 'plan' ? 'active' : ''}`} onClick={() => setResultTab('plan')}>
          执行计划 {plan ? <span className="da-count">1</span> : null}
        </button>
        <button className={`da-rtab ${resultTab === 'messages' ? 'active' : ''}`} onClick={() => setResultTab('messages')}>
          消息 {errorCount ? <span className="da-count">{errorCount}</span> : null}
        </button>
        <div className="da-result-actions">
          {result?.rows?.length ? (
            <button className="da-btn small ghost" onClick={onExport} title="导出当前结果为 CSV">
              <Icon name="download" size={12} /> CSV
            </button>
          ) : null}
          {result?.rows?.length ? (
            <button className="da-btn small ghost" onClick={onCopyJson} title="复制结果 JSON">
              <Icon name="copy" size={12} /> JSON
            </button>
          ) : null}
          {plan ? (
            <button className="da-btn small ghost" onClick={onCopyPlan} title="复制原始执行计划">
              <Icon name="copy" size={12} /> 计划
            </button>
          ) : null}
        </div>
      </div>

      {result ? (
        <div className="da-result-meta">
          <span className={`da-status ${result.status === 'ERROR' ? 'error' : 'ok'}`}>
            {result.status === 'ERROR' ? '失败' : '成功'}
          </span>
          <span>耗时 <strong>{formatDuration(result.elapsedMs)}</strong></span>
          <span>取数 <strong>{formatDuration(result.fetchMs)}</strong></span>
          <span><strong>{result.rowCount}</strong> 行</span>
          {result.truncated ? <span className="da-status warn">已截断</span> : null}
          <span>查询 <span className="da-mono">{String(result.queryId || '').slice(0, 8)}</span></span>
          <span style={{ marginLeft: 'auto' }}>{datasourceName}</span>
        </div>
      ) : null}

      <div className="da-grid-wrap" style={{ display: resultTab === 'data' ? 'block' : 'none' }}>        {hasResult && result.status !== 'ERROR' && columns.length ? (
          <table className="da-grid">
            <thead>
              <tr>
                {columns.map((column, index) => (
                  <th key={`${column.name}_${index}`} title={`${column.name} · ${column.typeName}`}>
                    <span className="da-th-name">{column.label || column.name}</span>
                    <span className="da-th-type">{column.typeName}</span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {result.rows.map((row, rowIndex) => (
                <tr key={rowIndex}>
                  {columns.map((column, columnIndex) => {
                    const value = row[columnIndex];
                    const isNumber = typeof value === 'number';
                    const isObject = value !== null && typeof value === 'object';
                    const text = value === null || value === undefined ? 'NULL' : (isObject ? JSON.stringify(value) : String(value));
                    return (
                      <td
                        key={`${rowIndex}_${columnIndex}`}
                        className={value === null ? 'null' : isNumber ? 'num' : isObject ? 'json' : ''}
                        title={text.length > 200 ? text.slice(0, 200) + '…' : text}
                      >
                        {text}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="da-empty">
            <div className="da-empty-icon"><Icon name="terminal" size={22} /></div>
            <div className="da-empty-title">{result?.status === 'ERROR' ? '执行失败' : '运行一条查询'}</div>
            <div className="da-empty-text">
              {result?.status === 'ERROR'
                ? result.message
                : '连接由云端统一管理，端侧不保存数据库密码。按 Ctrl+Enter 运行，Ctrl+Shift+Enter 获取执行计划。'}
            </div>
          </div>
        )}
      </div>

      {resultTab === 'plan' ? (
        plan ? <PlanView plan={plan} /> : (
          <div className="da-empty">
            <div className="da-empty-icon"><Icon name="plan" size={22} /></div>
            <div className="da-empty-title">还没有执行计划</div>
            <div className="da-empty-text">执行计划只读，不会真正运行查询。</div>
            <button className="da-btn" onClick={onExplain}><Icon name="plan" size={12} /> 获取执行计划</button>
          </div>
        )
      ) : null}

      {resultTab === 'messages' ? (
        <div className="da-scroll">
          {message || result?.message ? (
            <div className="da-error-box">{message || result?.message}</div>
          ) : null}
          {result ? (
            <div className="da-plan-summary">
              {`queryId: ${result.queryId || '—'}\nstatus: ${result.status || '—'}\nelapsedMs: ${result.elapsedMs ?? '—'}\nfetchMs: ${result.fetchMs ?? '—'}\nrowCount: ${result.rowCount ?? 0}\ntruncated: ${result.truncated ? 'true' : 'false'}\ncolumns: ${(result.columns || []).length}\nplan: ${plan ? plan.format : 'not requested'}`}
            </div>
          ) : null}
          {!message && !result ? (
            <div className="da-empty">
              <div className="da-empty-icon"><Icon name="info" size={22} /></div>
              <div className="da-empty-title">消息与校验</div>
              <div className="da-empty-text">这里会显示只读校验、超时、错误信息和查询元数据。所有执行记录都会自动写入云端历史。</div>
            </div>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

function PlanView({ plan }) {
  const insights = plan.insights || [];
  return (
    <div className="da-plan-view">
      <div className="da-plan-summary">{plan.summary || '执行计划已获取'}</div>
      <div className="da-insights">
        {insights.map((insight, index) => (
          <div className={`da-insight ${String(insight.level || 'info').toLowerCase()}`} key={`${insight.code}_${index}`}>
            <span className="da-sev-dot" />
            <div>
              <div className="da-insight-title">{insight.title}</div>
              <div className="da-insight-detail">{insight.detail}</div>
            </div>
          </div>
        ))}
      </div>
      <details className="da-plan-raw" open>
        <summary>原始执行计划 · {plan.format} · {formatDuration(plan.explainMs)}</summary>
        <pre>{plan.raw || '(empty)'}</pre>
      </details>
    </div>
  );
}

function AssistantPanel({ analysis, comparison, candidateSql, setCandidateSql, onAnalyze, onCompare, onCopyPrompt, onCopySql, busy, stats }) {
  return (
    <div className="da-scroll">
      <div className="da-assistant-intro">
        <div className="da-assistant-title"><Icon name="sparkles" size={15} className="da-spark" /> DSH SQL Copilot</div>
        <div className="da-assistant-text">
          静态检查 + 真实执行计划 + 云端基准对比。把提示词交给 DSH，模型可通过 MCP 工具继续取计划、跑对比、查团队知识库。
        </div>
        <div className="da-assistant-actions">
          <button className="da-btn small accent" onClick={onAnalyze} disabled={Boolean(busy)}>
            <Icon name={busy === 'analyze' ? 'refresh' : 'sparkles'} size={11} className={busy === 'analyze' ? 'da-spin' : ''} />
            分析当前 SQL
          </button>
          {analysis ? (
            <button className="da-btn small ghost" onClick={onCopyPrompt}>
              <Icon name="copy" size={11} /> 复制 DSH 提示词
            </button>
          ) : null}
        </div>
      </div>

      {stats ? (
        <div className="da-compare-result" style={{ marginTop: 10 }}>
          <div className="da-metric">
            <div className="da-metric-label">今日查询</div>
            <div className="da-metric-value">{stats.queriesToday}</div>
          </div>
          <div className="da-metric">
            <div className="da-metric-label">P95 耗时</div>
            <div className="da-metric-value">{formatDuration(stats.p95DurationMs)}</div>
          </div>
          <div className="da-metric">
            <div className="da-metric-label">团队知识</div>
            <div className="da-metric-value">{stats.knowledgeCount}</div>
          </div>
        </div>
      ) : null}

      {analysis ? (
        <>
          <div className="da-section">
            <div className="da-section-head">
              <div className="da-section-title"><Icon name="warning" size={12} /> 检查发现 · {analysis.findings?.length || 0}</div>
              <span className="da-muted" style={{ fontSize: 10 }}>{analysis.dialect}</span>
            </div>
            {(analysis.findings || []).map((finding, index) => (
              <div className="da-finding" key={`${finding.code}_${index}`}>
                <span className={`da-sev ${String(finding.severity || 'info').toLowerCase()}`}>{severityLabel(finding.severity)}</span>
                <div className="da-finding-main">
                  <div className="da-finding-title">{finding.title}</div>
                  <div className="da-finding-detail">{finding.detail}</div>
                  <div className="da-finding-suggest">建议：{finding.suggestion}</div>
                </div>
              </div>
            ))}
          </div>

          {analysis.plan ? (
            <div className="da-section">
              <div className="da-section-head">
                <div className="da-section-title"><Icon name="plan" size={12} /> 计划摘要</div>
              </div>
              <div className="da-plan-summary">{analysis.plan.summary}</div>
            </div>
          ) : null}

          <div className="da-section">
            <div className="da-section-head">
              <div className="da-section-title"><Icon name="terminal" size={12} /> 候选 SQL 基准对比</div>
            </div>
            <div className="da-code-box">
              <textarea
                value={candidateSql}
                onChange={(event) => setCandidateSql(event.target.value)}
                placeholder="粘贴模型改写后的只读 SQL，运行原 SQL 与候选 SQL 的耗时/计划对比"
                spellCheck={false}
              />
              <div className="da-code-actions">
                <button className="da-btn small ghost" onClick={() => onCopySql(candidateSql)} disabled={!candidateSql}>
                  <Icon name="copy" size={10} /> 复制
                </button>
                <button className="da-btn small accent" onClick={onCompare} disabled={!candidateSql.trim() || Boolean(busy)}>
                  <Icon name={busy === 'compare' ? 'refresh' : 'play'} size={10} className={busy === 'compare' ? 'da-spin' : ''} />
                  运行对比
                </button>
              </div>
            </div>

            {comparison ? (
              <>
                <div className="da-compare-result">
                  <div className="da-metric">
                    <div className="da-metric-label">原 SQL</div>
                    <div className="da-metric-value">{formatDuration(comparison.originalMs)}</div>
                  </div>
                  <div className="da-metric">
                    <div className="da-metric-label">候选 SQL</div>
                    <div className="da-metric-value">{formatDuration(comparison.candidateMs)}</div>
                  </div>
                  <div className="da-metric">
                    <div className="da-metric-label">变化</div>
                    <div className={`da-metric-value ${comparison.verdict === 'FASTER' ? 'good' : comparison.verdict === 'SLOWER' ? 'bad' : ''}`}>
                      {comparison.improvementPercent == null ? '—' : `${comparison.improvementPercent > 0 ? '-' : '+'}${Math.abs(comparison.improvementPercent).toFixed(1)}%`}
                    </div>
                  </div>
                </div>
                <div className={`da-status ${comparison.verdict === 'FASTER' ? 'ok' : comparison.verdict === 'SLOWER' ? 'error' : 'warn'}`} style={{ marginTop: 7 }}>
                  {verdictLabel(comparison.verdict)}
                </div>
                <div className="da-warn-box" style={{ marginTop: 8 }}>
                  {(comparison.warnings || []).map((warning, index) => <div key={index}>• {warning}</div>)}
                </div>
              </>
            ) : null}
          </div>

          <details className="da-prompt">
            <summary>查看完整的 DSH 优化提示词（{analysis.promptContext?.length || 0} 字符）</summary>
            <pre>{analysis.promptContext}</pre>
          </details>
        </>
      ) : (
        <div className="da-section">
          <div className="da-empty" style={{ minHeight: 180 }}>
            <div className="da-empty-icon"><Icon name="sparkles" size={22} /></div>
            <div className="da-empty-title">让 DSH 参与优化</div>
            <div className="da-empty-text">
              点击“分析当前 SQL”生成静态检查、执行计划和提示词。然后在 DSH 对话中粘贴提示词，模型会通过
              {' '}dataagent_explain_sql / dataagent_compare_sql / dataagent_search_knowledge 工具闭环验证。
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function HistoryPanel({ history, loading, query, setQuery, onSearch, onRefresh, onOpen, onSolve, stats }) {
  return (
    <div className="da-scroll">
      <div className="da-section-head">
        <div className="da-section-title"><Icon name="history" size={12} /> 最近执行</div>
        <button className="da-icon-btn" onClick={onRefresh} title="刷新历史"><Icon name="refresh" size={13} /></button>
      </div>
      <div className="da-search" style={{ margin: '0 0 10px' }}>
        <Icon name="search" size={12} />
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => { if (event.key === 'Enter') onSearch(); }}
          placeholder="搜索 SQL / 问题 / 标签"
        />
      </div>
      {stats ? (
        <div className="da-list-meta" style={{ marginBottom: 9 }}>
          <span>今日 {stats.queriesToday}</span>
          <span>慢查询 {stats.slowQueries}</span>
          <span>已解决 {stats.solvedQueries}</span>
        </div>
      ) : null}
      {loading ? <div className="da-tree-empty"><Icon name="refresh" size={14} className="da-spin" /> 加载中…</div> : null}
      {!loading && !history.length ? <div className="da-tree-empty">还没有执行历史。运行一条 SQL 后会自动记录。</div> : null}
      {history.map((item) => (
        <div className="da-list-item" key={item.id}>
          <div className="da-list-top">
            <span className={`da-status ${item.status === 'ERROR' ? 'error' : item.solved ? 'ok' : ''}`}>
              {item.kind === 'EXPLAIN' ? '计划' : item.status === 'ERROR' ? '失败' : item.solved ? '已沉淀' : '成功'}
            </span>
            <span className="da-list-title" title={item.sqlText}>{item.problem || item.sqlText.replace(/\s+/g, ' ').slice(0, 52)}</span>
            <span className="da-muted" style={{ fontSize: 10 }}>{formatDuration(item.durationMs)}</span>
          </div>
          <div className="da-list-sub da-mono" title={item.sqlText}>{item.sqlText.replace(/\s+/g, ' ').slice(0, 110)}</div>
          <div className="da-list-meta">
            <span>{formatTime(item.createdAt)}</span>
            <span>{item.datasourceName || item.dialect}</span>
            {item.tags ? <span>{item.tags}</span> : null}
            <span style={{ marginLeft: 'auto', display: 'flex', gap: 5 }}>
              <button className="da-btn small ghost" onClick={() => onOpen(item)}>载入</button>
              <button className="da-btn small ghost" onClick={() => onSolve(item)}>沉淀</button>
            </span>
          </div>
        </div>
      ))}
    </div>
  );
}

function KnowledgePanel({ items, loading, query, setQuery, onSearch, onRefresh, openId, setOpenId, onOpenSql, onUse, onCopy }) {
  return (
    <div className="da-scroll">
      <div className="da-section-head">
        <div className="da-section-title"><Icon name="book" size={12} /> 团队知识库</div>
        <button className="da-icon-btn" onClick={onRefresh} title="刷新知识库"><Icon name="refresh" size={13} /></button>
      </div>
      <div className="da-search" style={{ margin: '0 0 10px' }}>
        <Icon name="search" size={12} />
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => { if (event.key === 'Enter') onSearch(); }}
          placeholder="搜索问题 / SQL / 标签"
        />
      </div>
      {loading ? <div className="da-tree-empty"><Icon name="refresh" size={14} className="da-spin" /> 加载中…</div> : null}
      {!loading && !items.length ? (
        <div className="da-tree-empty">
          还没有知识条目。执行历史里点击“沉淀”，把问题、根因、优化 SQL 与验证结果留在团队知识库。
        </div>
      ) : null}
      {items.map((item) => {
        const open = openId === item.id;
        return (
          <div className="da-kb-card" key={item.id}>
            <div className="da-kb-title">{item.title}</div>
            <div className="da-kb-problem">{item.problem || '暂无问题描述'}</div>
            <div className="da-tags">
              {(item.tags ? item.tags.split(/[,，]/) : []).filter(Boolean).slice(0, 5).map((tag) => (
                <span className="da-tag" key={tag}>{tag}</span>
              ))}
              <span className="da-tag">{item.dialect || 'SQL'}</span>
              <span className="da-tag">引用 {item.usageCount}</span>
            </div>
            <div className="da-kb-actions">
              <button className="da-btn small ghost" onClick={() => setOpenId(open ? '' : item.id)}>
                <Icon name="chevron" size={10} /> {open ? '收起' : '详情'}
              </button>
              <button className="da-btn small ghost" onClick={() => onOpenSql(item.sqlText)}>载入原 SQL</button>
              {item.optimizedSql ? <button className="da-btn small accent" onClick={() => onOpenSql(item.optimizedSql)}>载入优化 SQL</button> : null}
              <button className="da-btn small ghost" onClick={() => onUse(item)}>引用 +1</button>
            </div>
            {open ? (
              <div className="da-kb-detail">
                {item.rootCause ? (
                  <>
                    <div className="da-kb-section-label">根因</div>
                    <div className="da-kb-text">{item.rootCause}</div>
                  </>
                ) : null}
                {item.optimizationSteps ? (
                  <>
                    <div className="da-kb-section-label">优化步骤</div>
                    <div className="da-kb-text">{item.optimizationSteps}</div>
                  </>
                ) : null}
                <div className="da-kb-section-label">原 SQL</div>
                <pre className="da-kb-sql">{item.sqlText}</pre>
                {item.optimizedSql ? (
                  <>
                    <div className="da-kb-section-label">优化 SQL</div>
                    <pre className="da-kb-sql">{item.optimizedSql}</pre>
                  </>
                ) : null}
                <div className="da-kb-section-label">验证</div>
                <div className="da-kb-text">
                  原 {formatDuration(item.beforeDurationMs)} → 后 {formatDuration(item.afterDurationMs)} · {formatTime(item.updatedAt)}
                </div>
                <div className="da-kb-actions">
                  <button className="da-btn small ghost" onClick={() => onCopy(`${item.problem || item.title}\n\n${item.optimizedSql || item.sqlText}`)}>
                    <Icon name="copy" size={10} /> 复制条目
                  </button>
                </div>
              </div>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}

function Modal({ title, children, footer, onClose, wide = false }) {
  React.useEffect(() => {
    function handleKey(event) {
      if (event.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [onClose]);

  return (
    <div className="da-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <div className={`da-modal ${wide ? 'wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="da-modal-head">
          <div className="da-modal-title">{title}</div>
          <button className="da-icon-btn" onClick={onClose} title="关闭"><Icon name="close" size={14} /></button>
        </div>
        <div className="da-modal-body">{children}</div>
        {footer ? <div className="da-modal-foot">{footer}</div> : null}
      </div>
    </div>
  );
}

function SettingsModal({ apiBase, token, theme, onTheme, onClose, onSave }) {
  const [draftBase, setDraftBase] = React.useState(apiBase);
  const [draftToken, setDraftToken] = React.useState(storageGet('dataagent.token', token));
  const [testState, setTestState] = React.useState(null);
  const [testing, setTesting] = React.useState(false);

  async function testConnection() {
    setTesting(true);
    setTestState(null);
    try {
      const client = createApi({ baseUrl: draftBase, token: draftToken });
      const health = await client.health();
      setTestState({ ok: true, message: `连接成功 · ${health.service} ${health.version}` });
    } catch (error) {
      setTestState({ ok: false, message: error.message });
    } finally {
      setTesting(false);
    }
  }

  function save() {
    storageSet('dataagent.apiBase', draftBase.trim());
    storageSet('dataagent.token', draftToken.trim());
    onSave(draftBase.trim(), draftToken.trim());
  }

  return (
    <Modal
      title="DataAgent 设置"
      onClose={onClose}
      footer={(
        <>
          <button className="da-btn ghost" onClick={onClose}>取消</button>
          <button className="da-btn ghost" onClick={testConnection} disabled={testing}>
            <Icon name={testing ? 'refresh' : 'terminal'} size={12} className={testing ? 'da-spin' : ''} /> 测试连接
          </button>
          <button className="da-btn primary" onClick={save}>保存</button>
        </>
      )}
    >
      {testState ? (
        <div className={testState.ok ? 'da-warn-box' : 'da-error-box'} style={testState.ok ? { color: 'var(--da-green)', background: 'var(--da-green-soft)', borderColor: 'color-mix(in srgb, var(--da-green) 35%, transparent)' } : undefined}>
          {testState.message}
        </div>
      ) : null}
      <div className="da-field">
        <label className="da-label">云端 API 地址</label>
        <input className="da-input" value={draftBase} onChange={(event) => setDraftBase(event.target.value)} placeholder={DEFAULT_API_BASE} />
        <div className="da-hint">DSH 面板与独立 Studio 共用该地址；默认指向本机 Spring Boot 服务。</div>
      </div>
      <div className="da-field">
        <label className="da-label">API Token</label>
        <input className="da-input" type="password" value={draftToken} onChange={(event) => setDraftToken(event.target.value)} placeholder={DEFAULT_TOKEN} />
        <div className="da-hint">对应云服务环境变量 DATAAGENT_API_TOKEN。生产环境请使用短期令牌，不要把长期密钥写进知识库。</div>
      </div>
      <div className="da-form-row">
        <div className="da-field">
          <label className="da-label">主题</label>
          <select className="da-select" value={theme} onChange={(event) => onTheme(event.target.value)}>
            <option value="dark">深色</option>
            <option value="light">浅色</option>
          </select>
        </div>
        <div className="da-field">
          <label className="da-label">执行模式</label>
          <input className="da-input" value="云端只读网关" readOnly />
        </div>
      </div>
      <div className="da-warn-box" style={{ marginTop: 4 }}>
        <strong>DSH 集成：</strong>安装 dsh-plugin 后，侧边栏会出现 DataAgent SQL 面板；MCP 工具通过同一云端 API 获取执行计划、运行基准对比、检索知识库。数据库密码只保存在云端加密存储中。
      </div>
    </Modal>
  );
}

function ConnectionModal({ api, onClose, onCreated }) {
  const [form, setForm] = React.useState({
    name: '',
    dialect: 'POSTGRESQL',
    jdbcUrl: '',
    username: '',
    password: '',
    maxRows: 500,
    queryTimeoutSeconds: 15,
    description: '',
  });
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState('');

  function update(key, value) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function submit() {
    setError('');
    if (!form.name.trim() || !form.jdbcUrl.trim()) {
      setError('连接名称和 JDBC URL 必填。');
      return;
    }
    setSaving(true);
    try {
      const created = await api.createDatasource({
        ...form,
        maxRows: Number(form.maxRows) || 500,
        queryTimeoutSeconds: Number(form.queryTimeoutSeconds) || 15,
      });
      await onCreated(created);
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      title="注册云端数据源"
      onClose={onClose}
      wide
      footer={(
        <>
          <button className="da-btn ghost" onClick={onClose}>取消</button>
          <button className="da-btn primary" onClick={submit} disabled={saving}>
            <Icon name={saving ? 'refresh' : 'database'} size={12} className={saving ? 'da-spin' : ''} /> 注册连接
          </button>
        </>
      )}
    >
      {error ? <div className="da-error-box">{error}</div> : null}
      <div className="da-form-row">
        <div className="da-field">
          <label className="da-label">连接名称</label>
          <input className="da-input" value={form.name} onChange={(event) => update('name', event.target.value)} placeholder="例如：订单生产库（只读）" />
        </div>
        <div className="da-field">
          <label className="da-label">方言</label>
          <select className="da-select" value={form.dialect} onChange={(event) => update('dialect', event.target.value)}>
            <option value="POSTGRESQL">PostgreSQL / openGauss / DWS</option>
            <option value="MYSQL">MySQL / MariaDB</option>
            <option value="H2">H2（本地演示）</option>
          </select>
        </div>
      </div>
      <div className="da-field">
        <label className="da-label">JDBC URL</label>
        <input className="da-input da-mono" value={form.jdbcUrl} onChange={(event) => update('jdbcUrl', event.target.value)} placeholder="jdbc:postgresql://db-host:5432/orders" />
        <div className="da-hint">只允许 PostgreSQL / openGauss / MySQL / H2；H2 禁止 INIT、RUNSCRIPT。生产建议只读账号 + 白名单网络。</div>
      </div>
      <div className="da-form-row">
        <div className="da-field">
          <label className="da-label">用户名</label>
          <input className="da-input" value={form.username} onChange={(event) => update('username', event.target.value)} autoComplete="off" />
        </div>
        <div className="da-field">
          <label className="da-label">密码</label>
          <input className="da-input" type="password" value={form.password} onChange={(event) => update('password', event.target.value)} autoComplete="new-password" />
          <div className="da-hint">AES-GCM 加密后存入云端元数据库，端侧永远不保存。</div>
        </div>
      </div>
      <div className="da-form-row">
        <div className="da-field">
          <label className="da-label">默认最大返回行数</label>
          <input className="da-input" type="number" min="1" max="5000" value={form.maxRows} onChange={(event) => update('maxRows', event.target.value)} />
        </div>
        <div className="da-field">
          <label className="da-label">查询超时（秒）</label>
          <input className="da-input" type="number" min="1" max="60" value={form.queryTimeoutSeconds} onChange={(event) => update('queryTimeoutSeconds', event.target.value)} />
        </div>
      </div>
      <div className="da-field">
        <label className="da-label">说明</label>
        <input className="da-input" value={form.description} onChange={(event) => update('description', event.target.value)} placeholder="连接用途、负责人、只读范围" />
      </div>
    </Modal>
  );
}

function SolveModal({ target, onClose, onSubmit, onCopy }) {
  const [form, setForm] = React.useState(target);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState('');

  function update(key, value) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function submit() {
    setError('');
    if (!form.title.trim()) {
      setError('标题必填。');
      return;
    }
    setSaving(true);
    try {
      await onSubmit(form);
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      title="沉淀到团队知识库"
      onClose={onClose}
      wide
      footer={(
        <>
          <button className="da-btn ghost" onClick={onClose}>取消</button>
          <button className="da-btn ghost" onClick={() => onCopy(target.item.sqlText)}><Icon name="copy" size={12} /> 复制原 SQL</button>
          <button className="da-btn primary" onClick={submit} disabled={saving}>
            <Icon name={saving ? 'refresh' : 'book'} size={12} className={saving ? 'da-spin' : ''} /> 保存知识
          </button>
        </>
      )}
    >
      {error ? <div className="da-error-box">{error}</div> : null}
      <div className="da-warn-box">
        记录“问题 → 原 SQL → 根因 → 优化 SQL → 验证结果”，后续团队可以直接检索复用。原执行 ID：<span className="da-mono">{target.item.id.slice(0, 8)}</span>
      </div>
      <div className="da-field">
        <label className="da-label">标题</label>
        <input className="da-input" value={form.title} onChange={(event) => update('title', event.target.value)} />
      </div>
      <div className="da-field">
        <label className="da-label">问题 / 现象</label>
        <textarea className="da-textarea" value={form.problem} onChange={(event) => update('problem', event.target.value)} />
      </div>
      <div className="da-field">
        <label className="da-label">根因</label>
        <textarea className="da-textarea" value={form.rootCause} onChange={(event) => update('rootCause', event.target.value)} placeholder="例如：WHERE 对索引列使用 YEAR()，导致无法范围扫描。" />
      </div>
      <div className="da-field">
        <label className="da-label">优化步骤 / 验证</label>
        <textarea className="da-textarea" value={form.optimizationSteps} onChange={(event) => update('optimizationSteps', event.target.value)} placeholder="改写方式、索引变更、EXPLAIN 对比、基准结果与风险。" />
      </div>
      <div className="da-field">
        <label className="da-label">优化后 SQL</label>
        <textarea className="da-textarea" value={form.optimizedSql} onChange={(event) => update('optimizedSql', event.target.value)} placeholder="可选。建议粘贴已验证的候选 SQL。" />
      </div>
      <div className="da-form-row">
        <div className="da-field">
          <label className="da-label">标签（逗号分隔）</label>
          <input className="da-input" value={form.tags} onChange={(event) => update('tags', event.target.value)} placeholder="慢查询,索引,聚合" />
        </div>
        <div className="da-field">
          <label className="da-label">优化后耗时（ms）</label>
          <input className="da-input" type="number" min="0" value={form.afterDurationMs} onChange={(event) => update('afterDurationMs', event.target.value)} placeholder="可选" />
        </div>
      </div>
      <div className="da-field">
        <label className="da-label">原 SQL（只读）</label>
        <textarea className="da-textarea" value={target.item.sqlText} readOnly style={{ minHeight: 100 }} />
      </div>
    </Modal>
  );
}
