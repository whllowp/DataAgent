const STYLE = `
:host { all: initial; }
* { box-sizing: border-box; }
.da-pet-wrap {
  position: fixed; right: 24px; bottom: 22px; z-index: 2147483000;
  width: 104px; height: 116px; user-select: none;
  font-family: ui-sans-serif, -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif;
  color: #e5edff;
}
.da-pet {
  position: absolute; right: 0; bottom: 0; width: 92px; height: 100px;
  cursor: grab; touch-action: none; filter: drop-shadow(0 12px 22px rgba(0,0,0,.38));
  transition: transform .2s ease;
}
.da-pet:hover { transform: translateY(-2px) scale(1.02); }
.da-pet:active { cursor: grabbing; }
.da-pet-body {
  position: absolute; left: 9px; bottom: 6px; width: 74px; height: 68px;
  border-radius: 46% 46% 44% 44% / 52% 52% 46% 46%;
  background: radial-gradient(circle at 34% 24%, #b9fff4 0, #52d9cd 22%, #23a9c8 60%, #146f9f 100%);
  box-shadow: inset -8px -10px 18px rgba(2, 24, 45, .30), inset 7px 7px 14px rgba(255,255,255,.25);
  animation: da-bob 3.4s ease-in-out infinite;
  border: 1px solid rgba(180, 255, 250, .45);
}
.da-pet::after {
  content: ""; position: absolute; left: 17px; bottom: 0; width: 58px; height: 10px;
  border-radius: 50%; background: rgba(0,0,0,.30); filter: blur(4px);
  animation: da-shadow 3.4s ease-in-out infinite;
}
.da-ear {
  position: absolute; top: -9px; width: 24px; height: 25px;
  background: linear-gradient(160deg, #7bf0e5, #1e8fb8);
  border: 1px solid rgba(180,255,250,.42);
}
.da-ear.left { left: 11px; border-radius: 80% 18% 58% 12%; transform: rotate(-18deg); }
.da-ear.right { right: 11px; border-radius: 18% 80% 12% 58%; transform: rotate(18deg); }
.da-ear::after {
  content: ""; position: absolute; inset: 6px 7px; border-radius: inherit;
  background: #0f6f8c; opacity: .62;
}
.da-eye {
  position: absolute; top: 27px; width: 18px; height: 20px; border-radius: 50%;
  background: #f7ffff; box-shadow: inset 0 -3px 5px rgba(0,0,0,.15);
}
.da-eye.left { left: 14px; }
.da-eye.right { right: 14px; }
.da-eye::after {
  content: ""; position: absolute; left: 5px; top: 5px; width: 8px; height: 10px;
  border-radius: 50%; background: #0b172b; animation: da-look 5s ease-in-out infinite;
}
.da-eye::before {
  content: ""; position: absolute; left: 9px; top: 5px; width: 4px; height: 4px;
  border-radius: 50%; background: #fff; z-index: 2;
}
.da-mouth {
  position: absolute; left: 31px; top: 50px; width: 12px; height: 6px;
  border-bottom: 2px solid rgba(8, 44, 66, .78); border-radius: 0 0 10px 10px;
}
.da-cheek {
  position: absolute; top: 45px; width: 9px; height: 5px; border-radius: 50%;
  background: rgba(255, 121, 165, .55); filter: blur(.2px);
}
.da-cheek.left { left: 7px; }
.da-cheek.right { right: 7px; }
.da-tail {
  position: absolute; right: -7px; bottom: 20px; width: 20px; height: 34px;
  border: 5px solid #37c9c4; border-left: 0; border-top: 0;
  border-radius: 0 0 16px 0; transform-origin: 4px 4px;
  animation: da-tail 2.8s ease-in-out infinite;
}
.da-antenna {
  position: absolute; left: 47px; top: -18px; width: 2px; height: 17px;
  background: #76eadf; transform: rotate(12deg); opacity: .8;
}
.da-antenna::after {
  content: ""; position: absolute; left: -3px; top: -5px; width: 8px; height: 8px;
  border-radius: 50%; background: #aaffef; box-shadow: 0 0 12px #34e7d6;
  animation: da-glow 2s ease-in-out infinite;
}
.da-pet-bubble {
  position: absolute; right: 70px; bottom: 78px; min-width: 122px; max-width: 190px;
  padding: 8px 10px; border-radius: 12px 12px 3px 12px;
  background: rgba(13, 25, 44, .92); border: 1px solid rgba(93, 230, 220, .33);
  box-shadow: 0 8px 22px rgba(0,0,0,.28); color: #dceaff;
  font-size: 11px; line-height: 1.45; white-space: nowrap;
  opacity: 0; transform: translateY(4px); pointer-events: none;
  transition: opacity .2s ease, transform .2s ease;
}
.da-pet:hover .da-pet-bubble, .da-pet-wrap.open .da-pet-bubble { opacity: 1; transform: translateY(0); }
.da-pet-wrap.open .da-pet-bubble { display: none; }
.da-pet[data-status="warn"] .da-pet-body {
  background: radial-gradient(circle at 34% 24%, #fff2b8 0, #f4c95d 22%, #e69535 60%, #a85c19 100%);
}
.da-pet[data-status="warn"] .da-tail { border-color: #e9b64f; }
.da-pet[data-status="down"] .da-pet-body {
  background: radial-gradient(circle at 34% 24%, #ffd3d8 0, #f07186 22%, #c4425e 60%, #84223b 100%);
}
.da-pet[data-status="down"] .da-tail { border-color: #d65b72; }
.da-pet[data-status="down"] .da-eye::after { background: #501527; }
.da-pet[data-status="warn"] .da-mouth,
.da-pet[data-status="down"] .da-mouth {
  border-bottom: 0; border-top: 2px solid rgba(8, 44, 66, .7); width: 10px;
  border-radius: 10px 10px 0 0;
}
.da-panel {
  position: fixed; right: 28px; bottom: 138px; z-index: 2147483001;
  width: 398px; max-width: calc(100vw - 32px); max-height: min(72vh, 650px);
  display: none; flex-direction: column; overflow: hidden;
  border-radius: 16px; border: 1px solid rgba(103, 224, 218, .25);
  background: linear-gradient(165deg, rgba(13, 25, 44, .98), rgba(8, 17, 31, .99));
  box-shadow: 0 26px 70px rgba(0,0,0,.55), 0 0 0 1px rgba(255,255,255,.025) inset;
  font-family: ui-sans-serif, -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif;
  color: #dce8fb; font-size: 12px;
}
.da-panel.open { display: flex; animation: da-panel-in .18s ease-out; }
.da-panel-head { display: flex; align-items: flex-start; justify-content: space-between; padding: 14px 15px 10px; border-bottom: 1px solid rgba(137, 180, 220, .12); }
.da-panel-title { display: flex; gap: 9px; align-items: center; font-weight: 700; font-size: 13px; color: #f2fbff; letter-spacing: .2px; }
.da-panel-title .da-logo { width: 25px; height: 25px; border-radius: 8px; display: grid; place-items: center; background: linear-gradient(145deg, #30d5c8, #2676d9); box-shadow: 0 4px 14px rgba(37, 190, 191, .28); font-size: 13px; }
.da-sub { color: #7d98b9; font-size: 10px; margin-top: 3px; font-weight: 400; }
.da-icon-btn { width: 25px; height: 25px; border: 0; border-radius: 7px; background: rgba(255,255,255,.06); color: #9eb6d0; cursor: pointer; font-size: 15px; line-height: 1; }
.da-icon-btn:hover { background: rgba(255,255,255,.12); color: #fff; }
.da-panel-body { padding: 12px 15px 4px; overflow: auto; }
.da-status-row { display: flex; align-items: center; gap: 7px; margin-bottom: 9px; color: #a6bbd4; }
.da-dot { width: 7px; height: 7px; border-radius: 50%; display: inline-block; box-shadow: 0 0 9px currentColor; }
.da-dot.ok { color: #43e3b5; background: #43e3b5; }
.da-dot.warn { color: #ffc95a; background: #ffc95a; }
.da-dot.down { color: #ff6f8d; background: #ff6f8d; }
.da-rca { border-radius: 11px; padding: 10px 11px; margin-bottom: 12px; border: 1px solid rgba(111, 219, 211, .16); background: rgba(38, 107, 130, .17); }
.da-rca.warn { border-color: rgba(255, 197, 90, .32); background: rgba(119, 83, 24, .22); }
.da-rca.down { border-color: rgba(255, 111, 141, .32); background: rgba(113, 28, 52, .23); }
.da-eyebrow { font-size: 9px; letter-spacing: 1.3px; text-transform: uppercase; color: #7ddbd4; margin-bottom: 4px; }
.da-rca.warn .da-eyebrow { color: #ffc971; }
.da-rca.down .da-eyebrow { color: #ff9caf; }
.da-rca b { color: #f4fdff; font-size: 13px; }
.da-rca p { margin: 4px 0 0; color: #a8bfd4; line-height: 1.5; }
.da-actions { margin-top: 6px; color: #76e6d3; font-size: 10px; }
.da-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 7px; margin-bottom: 12px; }
.da-metric { padding: 8px 9px; border-radius: 9px; background: rgba(255,255,255,.035); border: 1px solid rgba(157, 194, 225, .08); }
.da-metric span { display: block; color: #7892ae; font-size: 9px; margin-bottom: 3px; }
.da-metric strong { color: #e6f8ff; font-size: 14px; font-weight: 650; }
.da-metric small { display: block; color: #68829f; font-size: 9px; margin-top: 2px; }
.da-section { padding: 9px 0; border-top: 1px solid rgba(137, 180, 220, .10); }
.da-section-head { display: flex; justify-content: space-between; align-items: center; color: #8ea8c2; font-size: 10px; margin-bottom: 6px; }
.da-event { display: grid; grid-template-columns: 54px 1fr; gap: 8px; padding: 4px 0; color: #b8cee2; font-size: 10px; line-height: 1.4; }
.da-event time { color: #5f7b99; font-variant-numeric: tabular-nums; }
.da-event b { color: #e4f3ff; font-weight: 550; }
.da-event .da-err { color: #ff9db0; }
.da-mono { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 9px; color: #7f9db8; overflow-wrap: anywhere; }
.da-empty { color: #5e7894; font-size: 10px; padding: 4px 0; }
.da-footer { display: flex; gap: 7px; justify-content: flex-end; padding: 10px 15px 12px; border-top: 1px solid rgba(137, 180, 220, .10); }
.da-btn { border: 1px solid rgba(152, 202, 227, .16); background: rgba(255,255,255,.045); color: #bdd5ea; border-radius: 8px; font-size: 10px; padding: 6px 9px; cursor: pointer; }
.da-btn:hover { background: rgba(97, 227, 215, .10); border-color: rgba(97, 227, 215, .35); color: #eaffff; }
.da-btn.primary { background: linear-gradient(135deg, #1caeaa, #2e78c8); border-color: transparent; color: #f4ffff; }
.da-btn:disabled { opacity: .5; cursor: default; }
@keyframes da-bob { 0%,100% { transform: translateY(0) rotate(-1deg); } 50% { transform: translateY(-5px) rotate(1deg); } }
@keyframes da-shadow { 0%,100% { transform: scaleX(1); opacity: .55; } 50% { transform: scaleX(.86); opacity: .35; } }
@keyframes da-tail { 0%,100% { transform: rotate(-8deg); } 50% { transform: rotate(18deg); } }
@keyframes da-glow { 0%,100% { opacity: .45; } 50% { opacity: 1; } }
@keyframes da-look { 0%, 42% { transform: translateX(0); } 50%, 62% { transform: translateX(3px); } 70%, 100% { transform: translateX(0); } }
@keyframes da-panel-in { from { opacity: 0; transform: translateY(6px) scale(.985); } to { opacity: 1; transform: translateY(0) scale(1); } }
@media (max-width: 540px) {
  .da-panel { right: 12px; width: calc(100vw - 24px); bottom: 128px; }
  .da-pet-wrap { right: 14px; bottom: 14px; }
}
@media (prefers-reduced-motion: reduce) {
  .da-pet-body, .da-pet::after, .da-tail, .da-antenna::after, .da-eye::after { animation: none !important; }
}
`;

const STATUS = {
  ok: { label: '正常', dot: 'ok', bubble: '端云链路正常' },
  warn: { label: '需关注', dot: 'warn', bubble: '发现异常，点我看看' },
  down: { label: '离线', dot: 'down', bubble: '云端不可达，点我定位' },
};

function esc(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function clock(value) {
  if (!value) return '--:--';
  try {
    return new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  } catch {
    return '--:--';
  }
}

function duration(value) {
  const ms = Number(value);
  if (!Number.isFinite(ms)) return '—';
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${Math.round(ms)}ms`;
}

function apiBase() {
  try {
    return (window.localStorage.getItem('dataagent.apiBase') || 'http://127.0.0.1:8080').replace(/\/+$/, '');
  } catch {
    return 'http://127.0.0.1:8080';
  }
}

function apiToken() {
  try {
    return window.localStorage.getItem('dataagent.token') || 'dataagent-dev-token';
  } catch {
    return 'dataagent-dev-token';
  }
}

function diagnose(state) {
  const health = state.health;
  const ops = state.ops?.payload || null;
  const audit = state.audit?.payload || null;
  if (!health?.ok) {
    return {
      level: 'down', code: 'CLOUD_UNREACHABLE',
      title: '云端不可达',
      detail: `本地边缘无法连接 ${apiBase()}，端侧请求已进入降级。`,
      action: '检查 DataAgent 后端 8080、网络代理和 DSH 设置里的 API 地址。',
      evidence: [health?.error || 'health 请求失败', `endpoint=${apiBase()}`],
    };
  }
  if (health.latency > 1500) {
    return {
      level: 'warn', code: 'CLOUD_LATENCY',
      title: '云端链路变慢',
      detail: `health 往返 ${duration(health.latency)}，超过 1.5s。`,
      action: '检查本机网络、DSH 代理和云端 CPU/连接池。',
      evidence: [`health=${duration(health.latency)}`],
    };
  }
  const authFailure = [state.ops, state.audit, state.rsi]
    .find((item) => item && (item.status === 401 || item.status === 403));
  if (authFailure) {
    return {
      level: 'warn', code: 'AUTH_OR_ACL',
      title: 'API Token 或权限不足',
      detail: `云端返回 ${authFailure.status}，部分观测数据不可见。`,
      action: '检查 DSH 设置里的 API Token 角色；审计/RSI 需要 ADMIN。',
      evidence: [`status=${authFailure.status}`, `token=${apiToken().startsWith('da_') ? 'scoped' : 'legacy'}`],
    };
  }
  if (ops === null || state.ops?.ok === false) {
    return {
      level: 'warn', code: 'CLOUD_PARTIAL',
      title: '云端部分接口不可用',
      detail: 'health 正常，但 ops/audit 观测接口没有返回。',
      action: '查看后端日志与 /api/ops/overview；确认服务与租户上下文。',
      evidence: [state.ops?.error || 'ops 请求失败'],
    };
  }
  if (ops && Number(ops.failedQueries) > 0) {
    return {
      level: 'warn', code: 'DATASOURCE_FAILURE',
      title: '云端出现失败查询',
      detail: `最近失败 ${ops.failedQueries} 次，P95 ${duration(ops.p95DurationMs)}。`,
      action: '查看失败 SQL、数据源权限和连接池；优先排查最近变更的表。',
      evidence: [`failed=${ops.failedQueries}`, `p95=${duration(ops.p95DurationMs)}`],
    };
  }
  if (audit && Number(audit.blocked24h || 0) > 0) {
    return {
      level: 'warn', code: 'SQL_GUARD_BLOCK',
      title: 'SQL 守卫拦截',
      detail: `24h 内 ${audit.blocked24h} 次写操作/危险 SQL 被拦截。`,
      action: '确认是误操作还是权限策略；核对只读网关与 API Key 角色。',
      evidence: [`blocked24h=${audit.blocked24h}`],
    };
  }
  if (state.local.errors.length > 0) {
    return {
      level: 'warn', code: 'DSH_CLIENT_ERROR',
      title: 'DSH 客户端异常',
      detail: `本地捕获 ${state.local.errors.length} 个错误事件，优先排查插件/HMR。`,
      action: '刷新 DSH 页面；若仅本插件异常，查看下方本地事件堆栈。',
      evidence: state.local.errors.slice(0, 2).map((item) => item.message),
    };
  }
  return {
    level: 'ok', code: 'HEALTHY',
    title: '端云链路正常',
    detail: 'health、查询、审计与本地边缘事件均无异常。',
    action: '无需处理。',
    evidence: [`health=${duration(health.latency)}`, `queriesToday=${ops?.queriesToday ?? '—'}`],
  };
}

export function installDataAgentObservability(ctx) {
  if (typeof window === 'undefined' || typeof document === 'undefined') return;
  const previous = window.__DATAAGENT_OBSERVABILITY__;
  if (previous && typeof previous.destroy === 'function') previous.destroy();

  ctx.effect(() => {
    const state = {
      health: null, ops: null, audit: null, rsi: null,
      loading: true, lastRefresh: null, panelOpen: false,
      local: { errors: [], requests: [], startedAt: Date.now() },
      dispose: null,
    };
    const local = state.local;
    const originalFetch = window.fetch;
    let interval = 0;
    let disposed = false;

    const host = document.createElement('div');
    host.setAttribute('data-dataagent-observability', 'true');
    const shadow = host.attachShadow({ mode: 'open' });
    const style = document.createElement('style');
    style.textContent = STYLE;
    shadow.appendChild(style);

    const wrap = document.createElement('div');
    wrap.className = 'da-pet-wrap';
    wrap.innerHTML = `
      <div class="da-pet" role="button" tabindex="0" aria-label="析析：DataAgent 端云观测宠物" data-status="warn">
        <div class="da-pet-bubble">正在连接云端…</div>
        <div class="da-antenna"></div>
        <div class="da-ear left"></div><div class="da-ear right"></div>
        <div class="da-pet-body">
          <div class="da-eye left"></div><div class="da-eye right"></div>
          <div class="da-cheek left"></div><div class="da-cheek right"></div>
          <div class="da-mouth"></div>
        </div>
        <div class="da-tail"></div>
      </div>`;
    const panel = document.createElement('section');
    panel.className = 'da-panel';
    panel.setAttribute('aria-label', 'DataAgent 端云根因定位');
    shadow.appendChild(wrap);
    shadow.appendChild(panel);
    document.body.appendChild(host);

    const pet = wrap.querySelector('.da-pet');
    const bubble = wrap.querySelector('.da-pet-bubble');

    function pushError(source, message) {
      local.errors.unshift({ source, message: String(message || 'unknown'), at: new Date().toISOString() });
      if (local.errors.length > 12) local.errors.pop();
    }

    function onWindowError(event) {
      pushError('window.error', event.message || event.error?.message || 'script error');
    }
    function onRejection(event) {
      pushError('unhandledrejection', event.reason?.message || String(event.reason || 'unknown rejection'));
    }
    window.addEventListener('error', onWindowError);
    window.addEventListener('unhandledrejection', onRejection);

    if (originalFetch) {
      window.fetch = function dataAgentObservedFetch(input, init) {
        const url = typeof input === 'string' ? input : input?.url || '';
        const started = performance.now();
        const promise = originalFetch.call(this, input, init);
        if (url.includes('/api/')) {
          promise.then((response) => {
            local.requests.unshift({
              path: url.replace(apiBase(), '').slice(0, 90), status: response.status,
              ms: performance.now() - started, at: new Date().toISOString(),
            });
            if (local.requests.length > 30) local.requests.pop();
            if (!response.ok) pushError('cloud.http', `${response.status} ${url.replace(apiBase(), '')}`);
          }).catch((error) => {
            local.requests.unshift({
              path: url.replace(apiBase(), '').slice(0, 90), status: 0,
              ms: performance.now() - started, at: new Date().toISOString(),
            });
            if (local.requests.length > 30) local.requests.pop();
            pushError('cloud.network', error?.message || 'request failed');
          });
        }
        return promise;
      };
    }

    async function fetchCloud(path, timeout = 4500) {
      const controller = new AbortController();
      const timer = window.setTimeout(() => controller.abort(), timeout);
      const started = performance.now();
      try {
        const response = await window.fetch(apiBase() + path, {
          headers: { 'X-API-Key': apiToken(), 'Content-Type': 'application/json' },
          signal: controller.signal,
        });
        const text = await response.text();
        let payload = null;
        try { payload = text ? JSON.parse(text) : null; } catch { payload = text; }
        return { ok: response.ok, status: response.status, payload, latency: performance.now() - started };
      } catch (error) {
        return { ok: false, status: 0, payload: null, latency: performance.now() - started,
          error: error?.name === 'AbortError' ? '请求超时' : (error?.message || '网络错误') };
      } finally {
        window.clearTimeout(timer);
      }
    }

    async function refresh() {
      if (disposed) return;
      state.loading = true;
      render();
      const [health, ops, audit, rsi] = await Promise.all([
        fetchCloud('/api/health', 3000),
        fetchCloud('/api/ops/overview'),
        fetchCloud('/api/audit/overview'),
        fetchCloud('/api/rsi/overview'),
      ]);
      if (disposed) return;
      state.health = health;
      state.ops = ops;
      state.audit = audit;
      state.rsi = rsi;
      state.loading = false;
      state.lastRefresh = new Date().toISOString();
      render();
    }

    function metric(label, value, hint) {
      return `<div class="da-metric"><span>${esc(label)}</span><strong>${esc(value)}</strong>${hint ? `<small>${esc(hint)}</small>` : ''}</div>`;
    }

    function render() {
      const rca = diagnose(state);
      const status = STATUS[rca.level] || STATUS.warn;
      pet.dataset.status = rca.level;
      bubble.textContent = state.loading && !state.health ? '正在连接云端…' : status.bubble;
      panel.classList.toggle('open', state.panelOpen);
      wrap.classList.toggle('open', state.panelOpen);

      const ops = state.ops?.payload || {};
      const audit = state.audit?.payload || {};
      const rsi = state.rsi?.payload || {};
      const health = state.health?.payload || {};
      const cloudRequests = local.requests.filter((item) => !item.path.startsWith('/api/health')).slice(0, 5);
      const errors = local.errors.slice(0, 4);
      const cloudErrors = (ops.recentErrors || []).slice(0, 3);
      const errorCount = Number(audit.failed24h || 0) + local.errors.length;

      panel.innerHTML = `
        <div class="da-panel-head">
          <div class="da-panel-title"><span class="da-logo">◉</span><div>DataAgent 端云观测<div class="da-sub">本地边缘 + 云端 · 根因定位</div></div></div>
          <button class="da-icon-btn" data-action="close" aria-label="关闭">×</button>
        </div>
        <div class="da-panel-body">
          <div class="da-status-row">
            <span class="da-dot ${status.dot}"></span>
            <b>${esc(status.label)}</b>
            <span>· ${esc(health.status || (state.health?.ok ? 'UP' : 'DOWN'))}</span>
            <span>· ${esc(apiBase().replace(/^https?:\/\//, ''))}</span>
            <span style="margin-left:auto;color:#5f7b99">${state.loading ? '刷新中' : clock(state.lastRefresh)}</span>
          </div>
          <div class="da-rca ${esc(rca.level)}">
            <div class="da-eyebrow">Root cause · 候选</div>
            <b>${esc(rca.title)}</b>
            <p>${esc(rca.detail)}</p>
            <div class="da-actions">→ ${esc(rca.action)}</div>
          </div>
          <div class="da-grid">
            ${metric('health 往返', duration(state.health?.latency), state.health?.ok ? '云端/API' : '本地不可达')}
            ${metric('今日查询', ops.queriesToday ?? '—', `失败 ${ops.failedQueries ?? '—'} · 慢 ${ops.slowQueries ?? '—'}`)}
            ${metric('P95 延迟', duration(ops.p95DurationMs), `平均 ${duration(ops.averageDurationMs)}`)}
            ${metric('本地事件', String(errorCount), `${health.databaseVersion || ops.status || '—'}`)}
          </div>
          <div class="da-section">
            <div class="da-section-head"><span>本地边缘 · API 观测</span><span>${local.requests.length} 条</span></div>
            ${cloudRequests.length ? cloudRequests.map((item) => `
              <div class="da-event"><time>${clock(item.at)}</time><span><b class="${item.status && item.status >= 400 ? 'da-err' : ''}">${esc(item.status || 'ERR')}</b> ${esc(item.path)} <span class="da-mono">${duration(item.ms)}</span></span></div>`).join('')
              : '<div class="da-empty">暂无 API 请求；打开 SQL Studio 后开始采样。</div>'}
          </div>
          <div class="da-section">
            <div class="da-section-head"><span>本地异常</span><span>${errors.length ? '需关注' : '无'}</span></div>
            ${errors.length ? errors.map((item) => `
              <div class="da-event"><time>${clock(item.at)}</time><span><b class="da-err">${esc(item.source)}</b><br/>${esc(item.message)}</span></div>`).join('')
              : '<div class="da-empty">插件、HMR、网络均未捕获异常。</div>'}
          </div>
          <div class="da-section">
            <div class="da-section-head"><span>云端最近错误</span><span>${ops.recentErrors?.length || 0}</span></div>
            ${cloudErrors.length ? cloudErrors.map((item) => `
              <div class="da-event"><time>${clock(item.createdAt)}</time><span><b class="da-err">${esc(item.errorMessage || '查询失败')}</b><br/><span class="da-mono">${esc((item.sqlText || '').slice(0, 120))}</span></span></div>`).join('')
              : '<div class="da-empty">没有失败查询记录。</div>'}
          </div>
          <div class="da-section">
            <div class="da-section-head"><span>协同上下文</span><span>只读</span></div>
            <div class="da-mono">edge=${esc(location.host)} · cloud=${esc(apiBase())} · rsi=${esc(rsi.runs ?? '—')} runs · audit=${esc(audit.last24h ?? audit.total ?? '—')}</div>
          </div>
        </div>
        <div class="da-footer">
          <button class="da-btn" data-action="copy">复制诊断</button>
          <button class="da-btn primary" data-action="refresh" ${state.loading ? 'disabled' : ''}>${state.loading ? '刷新中…' : '刷新'}</button>
        </div>`;
    }

    async function copyDiagnostic() {
      const payload = {
        at: new Date().toISOString(),
        edge: { host: location.host, api: apiBase(), requests: local.requests.slice(0, 10), errors: local.errors },
        cloud: { health: state.health, ops: state.ops, audit: state.audit, rsi: state.rsi },
        diagnosis: diagnose(state),
      };
      try {
        await navigator.clipboard.writeText(JSON.stringify(payload, null, 2));
        bubble.textContent = '诊断已复制';
      } catch {
        bubble.textContent = '复制失败，请手动查看';
      }
      window.setTimeout(() => { if (!state.panelOpen) bubble.textContent = STATUS[diagnose(state).level].bubble; }, 1800);
    }

    function togglePanel() {
      state.panelOpen = !state.panelOpen;
      render();
    }

    pet.addEventListener('click', (event) => {
      if (pet.dataset.dragged === 'true') {
        pet.dataset.dragged = 'false';
        return;
      }
      event.stopPropagation();
      togglePanel();
    });
    pet.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); togglePanel(); }
    });
    panel.addEventListener('click', (event) => {
      const action = event.target?.closest?.('[data-action]')?.dataset.action;
      if (action === 'close') { state.panelOpen = false; render(); }
      if (action === 'refresh') refresh();
      if (action === 'copy') copyDiagnostic();
    });
    const onKey = (event) => { if (event.key === 'Escape' && state.panelOpen) { state.panelOpen = false; render(); } };
    document.addEventListener('keydown', onKey);

    // Drag the pet; a click without movement toggles the panel.
    let drag = null;
    pet.addEventListener('pointerdown', (event) => {
      drag = { x: event.clientX, y: event.clientY, right: 24, bottom: 22, moved: false };
      pet.setPointerCapture?.(event.pointerId);
    });
    pet.addEventListener('pointermove', (event) => {
      if (!drag) return;
      const dx = drag.x - event.clientX;
      const dy = drag.y - event.clientY;
      if (Math.abs(dx) + Math.abs(dy) > 5) drag.moved = true;
      if (!drag.moved) return;
      wrap.style.right = `${Math.max(8, Math.min(window.innerWidth - 112, drag.right + dx))}px`;
      wrap.style.bottom = `${Math.max(8, Math.min(window.innerHeight - 122, drag.bottom + dy))}px`;
      panel.style.right = wrap.style.right;
      panel.style.bottom = `${Math.max(8, Math.min(window.innerHeight - 122, drag.bottom + dy)) + 116}px`;
    });
    pet.addEventListener('pointerup', () => {
      if (drag?.moved) pet.dataset.dragged = 'true';
      drag = null;
    });

    render();
    refresh();
    interval = window.setInterval(refresh, 20_000);
    state.dispose = () => {
      disposed = true;
      window.clearInterval(interval);
      window.removeEventListener('error', onWindowError);
      window.removeEventListener('unhandledrejection', onRejection);
      document.removeEventListener('keydown', onKey);
      if (originalFetch) window.fetch = originalFetch;
      host.remove();
      delete window.__DATAAGENT_OBSERVABILITY__;
    };
    window.__DATAAGENT_OBSERVABILITY__ = state;
    return () => state.dispose?.();
  }, 'dataagent: observability + desktop pet');
}
