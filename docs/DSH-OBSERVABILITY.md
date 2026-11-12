# DSH 端云可观测插件 / 桌面小宠物

DataAgent DSH bundle 现在包含一个**浏览器侧本地可观测插件**：

- 本地边缘：捕获 window error、unhandled rejection、DataAgent API 请求状态/耗时
- 云端：轮询 `/api/health`、`/api/ops/overview`、`/api/audit/overview`、`/api/rsi/overview`
- 根因候选：把端侧错误与云端 health/查询/审计/RSI 合并成一条 RCA
- 桌面小宠物：右下角“小析”，随链路健康度变色，可拖动、点击打开观测面板
- 诊断复制：一键复制脱敏的端云上下文，便于贴给云端值班/Agent

## 1. 组成

```text
dsh-plugin/
├─ index.js              Host 侧占位
├─ client.js             浏览器 bundle（构建生成）
├─ cordis.patch.yml      profile bundle + dataagent MCP
└─ locale/               面板元数据
studio/src/
├─ dsh-entry.jsx         注册 SQL 面板、可观测插件
└─ observability.js      边缘采集、RCA、浮动宠物
```

## 2. 安装 / 更新

`dsh-plugin` 目录本身就是 DSH profile 的 bundle；在 DSH「添加插件」里选择：

```text
E:\DataAgent\dataagent\dsh-plugin
```

或让 profile 中的 `@local/dataagent-dsh` 指向该目录。更新：

```powershell
cd E:\DataAgent\dataagent\studio
npm run build
```

然后刷新 DSH 页面。`client.js` 是构建产物，不提交 Git。

## 3. 根因规则

| 规则 | 触发 | 建议 |
| --- | --- | --- |
| `CLOUD_UNREACHABLE` | `/api/health` 不可达 | 检查 8080、网络、API 地址 |
| `CLOUD_LATENCY` | health > 1.5s | 检查代理/CPU/连接池 |
| `AUTH_OR_ACL` | ops/audit 返回 401/403 | 检查 API Token 角色；audit 需要 ADMIN |
| `CLOUD_PARTIAL` | health 正常但 ops 失败 | 查看后端日志与租户上下文 |
| `DATASOURCE_FAILURE` | `failedQueries > 0` | 查看失败 SQL、权限、连接池 |
| `SQL_GUARD_BLOCK` | 24h 有 blocked | 检查只读网关与角色 |
| `DSH_CLIENT_ERROR` | 本地 window error | 刷新 DSH / 检查 HMR/插件 |
| `HEALTHY` | 以上都正常 | 无需处理 |

## 4. 本地边缘数据

插件只保留内存中的最近 30 条 API 请求和 12 条错误，不落盘：

- `error` / `unhandledrejection`
- DataAgent `/api/` 请求的 status、duration、path
- 当前 DSH 页面 origin、API base

> 插件不读取 SQL 内容，不读取 token 明文，不写 localStorage 业务数据；只读取已有的 `dataagent.apiBase` / `dataagent.token` 设置用于请求。

## 5. 小析 / 桌面宠物

- 右下角浮动，不遮挡主内容
- 颜色：青色正常、黄色需关注、红色离线
- hover 显示一句话状态
- 点击打开面板；可拖动
- 面板展示 RCA、health 往返、今日查询、P95、本地 API、异常、云端错误、RSI 运行数
- “复制诊断”输出 JSON，可交给 DSH Agent 或云端值班

## 6. CORS

DSH Web GUI 可能是 `http://127.0.0.1:<随机端口>` 或 `dsh-app://`。默认 CORS 已允许：

- `http://127.0.0.1:*`
- `http://localhost:*`
- `dsh-app://*`

生产环境应收紧到实际 DSH origin。

## 7. 边界

- 这是 DSH 页面内的浮动宠物，不是独立 Electron 透明窗口。
- 本地观测是浏览器边缘视角，不是 eBPF/主机级 tracing。
- RCA 是规则引擎，不是 LLM 诊断；后续可把 `copyDiagnostic` JSON 交给 DataAgent MCP 做深度分析。
- 轮询 20s；页面关闭/插件卸载后停止并恢复 `window.fetch`。
