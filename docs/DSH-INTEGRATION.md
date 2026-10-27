# DSH 集成指南

DataAgent 不 fork DSH，而是交付一个 **bundle**：

- `dsh.client`：向 DSH Web UI 注册 `DataAgent SQL` 主面板和侧边栏入口；
- `dsh.bundle.patch`：同时插入一个 `@deepseek-ai/dsh-mcp-client` 行，把 12 个工具挂到当前 profile。

这样 DSH 升级不会覆盖你的改动，卸载也只需移除 bundle。

## 1. 前置条件

1. 云端已启动：`http://127.0.0.1:8080/api/health` 返回 `UP`。
2. 已构建前端：

```powershell
cd E:\DataAgent\dataagent\studio
npm install
npm run build
```

构建会生成：

- `backend/src/main/resources/static/studio/*`：独立 Studio；
- `dsh-plugin/client.js`：DSH 浏览器 bundle（React external）。

3. DSH 版本为当前桌面版（`0.2.1-rc.2` 验证环境）。插件使用 `slots`、`locale` 等稳定 Client API；不同大版本若 Slot 名称变化，需要按 DSH 文档调整 `studio/src/dsh-entry.jsx`。

## 2. 安装 bundle

### 方式 A：DSH 插件页面（推荐）

1. DSH 左侧栏 → **Plugins / 插件**。
2. **Add plugin / 添加插件** → **Install a third-party plugin**。
3. 选择本地目录：

```text
E:\DataAgent\dataagent\dsh-plugin
```

4. 安装完成后启用 **DataAgent SQL** bundle。
5. 如果 DSH 提示重启，重启；支持 HMR 的 profile 会直接加载。
6. 刷新页面，左侧栏应出现数据库图标 **DataAgent SQL**。

> 安装的是本地链接（link），后续修改 `client.js` 后重新构建；若 bundle 已被加载，DSH 可能需要重启才能加载新的 JavaScript generation。

### 方式 B：命令行（了解即可）

`dsh plugin` 只负责包管理；bundle 选择仍由 profile 管理。不建议手改 `$DSH_PROFILE_DIR/package.json` 或 `cordis.patch.yml`，优先用插件页面，避免破坏 profile 层级。

## 3. bundle 结构

```text
dsh-plugin/
├─ package.json
├─ cordis.patch.yml
├─ index.js              # Host 入口：空 apply
├─ client.js             # 构建产物：window.__ModuleLoader__.load(...)
├─ locale/
│  ├─ zh.json
│  └─ en.json
└─ icon.svg
```

`package.json` 关键字段：

```json
{
  "name": "@local/dataagent-dsh",
  "exports": {
    ".": "./index.js",
    "./client": "./client.js",
    "./locale/*.json": "./locale/*.json",
    "./icon": "./icon.svg"
  },
  "dsh": {
    "bundle": { "patch": "./cordis.patch.yml" },
    "client": {
      "platform": "web",
      "immediately": true,
      "inject": [
        "@deepseek-ai/dsh-client-ui-layout",
        "@deepseek-ai/dsh-client-ui-sidebar"
      ]
    }
  }
}
```

`client.js` 的 id 必须与包名一致：

```js
window.__ModuleLoader__.load({
  id: '@local/dataagent-dsh',
  factory(require) {
    // React 来自 DSH 模块表，不重复打包
    return { inject: ['slots', 'locale'], apply(ctx) { ... } };
  }
});
```

面板注册：

```js
ctx.slots.inject('main', () => ctx.slots.register({
  name: 'main',
  key: 'dataagent',
  locale: 'dataagent',
}, Panel));

ctx.slots.inject('sidebar.panellist', () => ctx.slots.register({
  name: 'sidebar.panellist',
  id: 'dataagent',
  order: 20,
  label: () => t('panel'),
  locale: 'dataagent',
}, Icon));
```

## 4. MCP 配置

`cordis.patch.yml` 同时插入：

```yaml
- insert:
    - id: dataagent-sql-studio
      name: '@local/dataagent-dsh'
      config: {}
    - id: dataagent-mcp
      name: '@deepseek-ai/dsh-mcp-client'
      config:
        serverName: dataagent
        transport: stdio
        command: node
        args:
          - E:/DataAgent/dataagent/mcp-server/server.js
        env:
          DATAAGENT_URL: http://127.0.0.1:8080
          DATAAGENT_TOKEN: dataagent-dev-token
        failOnStartupError: false
```

要点：

- `serverName: dataagent` → 工具在 DSH 中显示为 `mcp__dataagent__dataagent_*`。
- `command: node` 必须在 DSH 进程的 PATH 中；否则改成绝对路径，例如 `D:/tools/nodejs/node.exe`。
- `args` 是绝对路径；仓库移动后必须同步修改。
- `failOnStartupError: false`：云端未启动时不会让整个 profile 启动失败；此时工具不可用，启动云端后重启 DSH 或对应行。
- `DATAAGENT_TOKEN` 建议不要硬编码在 patch 里，生产可用环境变量/凭据服务；当前为本地 MVP。

### 手动测试 MCP 服务

```powershell
@(
'{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05"}}',
'{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
) -join "`n" | node E:\DataAgent\dataagent\mcp-server\server.js
```

应看到 `initialize` 与 12 个工具的 JSON 响应。工具调用示例：

```json
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"dataagent_explain_sql","arguments":{"sql":"SELECT * FROM customers LIMIT 5"}}}
```

## 5. 在 DSH 对话中使用

安装并重启后，直接对 DSH 说：

> 帮我优化这条 SQL：`SELECT * FROM sales_orders WHERE status='PAID' ORDER BY order_date DESC`。先用 dataagent_explain_sql 看计划，再给出候选 SQL，最后用 dataagent_compare_sql 验证。

DSH 会自动调用：

1. `dataagent_list_datasources` / `dataagent_list_tables`
2. `dataagent_describe_table`
3. `dataagent_explain_sql`
4. `dataagent_analyze_sql`
5. `dataagent_compare_sql`
6. `dataagent_search_knowledge`
7. 用户确认后 `dataagent_mark_solved` / `dataagent_save_knowledge`

### 推荐 Prompt（面板会自动生成完整版）

```text
你是 DataAgent 的 SQL 优化专家。请先调用 dataagent_explain_sql 获取真实计划，
必要时用 dataagent_describe_table 确认索引；给出候选 SQL 后，
用 dataagent_compare_sql 做只读基准对比。不要编造表结构或执行结果。
输出：诊断、证据、候选 SQL、风险、验证步骤。
```

面板中的 **“复制 DSH 提示词”** 会把当前 SQL、真实计划、静态发现、可用工具和输出 JSON schema 一起复制。

## 6. 验证清单

- [ ] `http://127.0.0.1:8080/api/health` 为 UP。
- [ ] Studio 能打开并执行 Demo 查询。
- [ ] DSH 左侧栏出现 **DataAgent SQL**。
- [ ] 点击后主面板显示对象浏览器 / 编辑器 / 结果区。
- [ ] 切换数据源能加载 schema 和表。
- [ ] Ctrl+Enter 执行返回结果；计划页显示原始计划。
- [ ] “AI 优化”返回 findings 和 Prompt。
- [ ] 对话中能看到 `mcp__dataagent__...` 工具并被调用。
- [ ] “沉淀”后知识库出现条目。

> 本仓库的自动化验证覆盖：Java 测试、Studio 独立页（无头 Chrome 截图 + 交互）、MCP stdio 工具调用、DSH client bundle 的模块加载。  
> DSH 面板的最终视觉/交互需要在你的 DSH 安装中安装 bundle 后确认；不同 DSH 版本的 Slot 实现可能变化。

## 7. 常见问题

### 侧边栏没有 DataAgent SQL

1. 确认 bundle 在 Plugins 页面为启用状态。
2. 看 DSH Host 日志是否有 `client` 模块加载失败。
3. 确认 `client.js` 的 `id` 与包名一致。
4. 修改 `client.js` 后重启 DSH（或确认 HMR 已加载新 generation）。

### 面板空白 / 控制台 `slot entry crashed`

- 按 F12 看 console。常见原因：
  - React 被重复打包（必须 `external: react`）；
  - `require('react')` 不可用；
  - 组件抛异常。
- 重新构建：`cd studio && npm run build`。
- 用本仓库的 Node 模块加载冒烟：

```powershell
cd studio
node -e "global.window={__ModuleLoader__:{load({id,factory}){const m=factory(require);console.log(id,Object.keys(m))}}};require('../dsh-plugin/client.js')"
```

### MCP 工具不可见

1. `cordis.patch.yml` 的 `dataagent-mcp` 行是否启用。
2. `node` 是否在 PATH；`args` 路径是否正确。
3. 手动运行第 4 节的 stdio 测试。
4. 云端是否启动、Token 是否一致。
5. DSH 可能需要重启才能重新建立 stdio 连接。

### 401 / CORS

- 401：Studio 设置里的 Token 与 `DATAAGENT_API_TOKEN` 不一致。
- CORS：DSH Web GUI 的 origin 不在 `DATAAGENT_CORS_ORIGINS`；把实际 origin 加进去，或本地开发用 `*`。

### 面板能加载，但没有数据

- 数据源列表为空：先注册数据源；Demo 库需要 `DATAAGENT_DEMO_ENABLED=true`。
- 元数据为空：检查连接、schema、数据库账号权限。
- 执行报错：看“消息”页；云端也会记录失败历史。

### 知识库/历史为空

- 执行一次查询后历史才会出现；
- 知识库需要点击“沉淀”或调用 `dataagent_mark_solved`；
- 演示库首次启动会预置 2 条知识条目（仅当历史为空）。

## 8. 二次开发

### 修改 UI

- 主界面：`studio/src/SqlStudio.jsx`
- 样式：`studio/src/styles.js`（内联 `<style>`，随组件卸载）
- 图标：`studio/src/icons.jsx`
- API 客户端：`studio/src/api.js`
- DSH 入口：`studio/src/dsh-entry.jsx`
- 构建：`studio/build.mjs`

### 修改 MCP 工具

- `mcp-server/server.js` 的 `tools` 数组；
- 每个工具包含 `name`、`description`、`inputSchema`、`handler`；
- 保持 `dataagent_` 前缀，便于模型识别；
- 调用云端 REST，不要在 MCP 进程里直连业务库。

### 修改 Host 行为

`dsh-plugin/index.js` 当前为空。若要在 Host 侧注册工具/系统提示，可参考 DSH 的 Cordis 插件文档；浏览器面板和 MCP 已覆盖 MVP 需求。

## 9. 限制

- 不做 DataGrip 的自动补全、语法高亮、ER 图、事务编辑、SSH 隧道。
- 面板的 React bundle 约 136KB（gzip 前），压缩后更小；不重复打包 React。
- 结果网格未虚拟化，默认最多 500 行；大结果集请用 SQL 聚合/分页。
- MCP 是 stdio 单进程，多个 DSH 会话共享；工具调用串行/异步由 DSH 调度。
- 当前 bundle 针对 `web` 平台；桌面版 DSH 使用同一 Web UI，因此可加载；若未来 desktop 平台有独立 Client 构建，需要调整 `dsh.client.platform`。
