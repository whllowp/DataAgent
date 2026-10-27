export const css = String.raw`
body.da-standalone {
  height: 100vh;
  margin: 0;
  overflow: hidden;
}

body.da-standalone #root {
  height: 100vh;
}

body.da-standalone .da-app {
  height: 100vh;
}

.da-app {
  --da-bg: var(--dsw-alias-bg-base, #0d1117);
  --da-panel: var(--dsw-alias-bg-layer-1, #11161d);
  --da-panel-2: var(--dsw-alias-bg-layer-2, #161c25);
  --da-raised: var(--dsw-alias-bg-layer-3, #1b2330);
  --da-border: var(--dsw-alias-border-l2, #273140);
  --da-border-strong: var(--dsw-alias-border-l3, #344154);
  --da-text: var(--dsw-alias-label-primary, #e6edf3);
  --da-text-2: var(--dsw-alias-label-secondary, #b6c2d1);
  --da-muted: var(--dsw-alias-label-tertiary, #8b98a9);
  --da-faint: var(--dsw-alias-label-quaternary, #5d6b7d);
  --da-accent: var(--dsw-alias-brand-primary, #4c9aff);
  --da-accent-soft: color-mix(in srgb, var(--da-accent) 15%, transparent);
  --da-green: #3fb950;
  --da-green-soft: rgba(63, 185, 80, 0.12);
  --da-red: #f85149;
  --da-red-soft: rgba(248, 81, 73, 0.12);
  --da-amber: #d29922;
  --da-amber-soft: rgba(210, 153, 34, 0.13);
  --da-purple: #bc8cff;
  --da-purple-soft: rgba(188, 140, 255, 0.12);
  --da-cyan: #39c5cf;
  --da-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
  --da-code: "Cascadia Code", "JetBrains Mono", "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  --da-ui: "Segoe UI", "Microsoft YaHei UI", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, sans-serif;
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-height: 540px;
  overflow: hidden;
  background: var(--da-bg);
  color: var(--da-text);
  font-family: var(--da-ui);
  font-size: 13px;
  line-height: 1.45;
  color-scheme: dark;
}

.da-app[data-theme="light"] {
  --da-bg: #f4f6f9;
  --da-panel: #ffffff;
  --da-panel-2: #f7f9fb;
  --da-raised: #eef2f7;
  --da-border: #dbe1e9;
  --da-border-strong: #c6cfdb;
  --da-text: #1b2430;
  --da-text-2: #3c4a5c;
  --da-muted: #66758a;
  --da-faint: #96a2b3;
  --da-accent: #0969da;
  --da-green: #1a7f37;
  --da-red: #cf222e;
  --da-amber: #9a6700;
  --da-purple: #8250df;
  --da-cyan: #0b7285;
  --da-shadow: 0 16px 48px rgba(20, 30, 45, 0.18);
  color-scheme: light;
}

.da-app *,
.da-app *::before,
.da-app *::after {
  box-sizing: border-box;
}

.da-app button,
.da-app input,
.da-app textarea,
.da-app select {
  font: inherit;
  color: inherit;
}

.da-app button {
  cursor: pointer;
}

.da-app button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.da-app ::selection {
  background: color-mix(in srgb, var(--da-accent) 35%, transparent);
}

.da-app ::-webkit-scrollbar {
  width: 10px;
  height: 10px;
}

.da-app ::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--da-muted) 32%, transparent);
  border-radius: 10px;
  border: 3px solid transparent;
  background-clip: padding-box;
}

.da-app ::-webkit-scrollbar-track {
  background: transparent;
}

.da-header {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 50px;
  padding: 0 14px;
  border-bottom: 1px solid var(--da-border);
  background: var(--da-panel);
  flex-shrink: 0;
}

.da-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.da-logo {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  color: #fff;
  background: linear-gradient(145deg, #2f81f7, #1f6feb 55%, #8250df);
  box-shadow: 0 4px 14px rgba(47, 129, 247, 0.25);
  flex-shrink: 0;
}

.da-brand-name {
  font-size: 14px;
  font-weight: 650;
  letter-spacing: 0.2px;
  white-space: nowrap;
}

.da-brand-sub {
  font-size: 11px;
  color: var(--da-muted);
  white-space: nowrap;
}

.da-header-spacer {
  flex: 1;
}

.da-ds-select {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding: 0 8px 0 10px;
  height: 30px;
  border: 1px solid var(--da-border);
  border-radius: 7px;
  background: var(--da-panel-2);
}

.da-ds-select select {
  max-width: 180px;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 12px;
  font-weight: 550;
}

.da-ds-select .da-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--da-green);
  box-shadow: 0 0 0 3px var(--da-green-soft);
  flex-shrink: 0;
}

.da-ds-select .da-dot.off {
  background: var(--da-faint);
  box-shadow: 0 0 0 3px var(--da-raised);
}

.da-top-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.da-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 30px;
  min-width: 30px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: var(--da-text-2);
  white-space: nowrap;
}

.da-icon-btn:hover {
  background: var(--da-raised);
  color: var(--da-text);
}

.da-icon-btn.active {
  background: var(--da-accent-soft);
  border-color: color-mix(in srgb, var(--da-accent) 30%, transparent);
  color: var(--da-accent);
}

.da-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 30px;
  padding: 0 11px;
  border: 1px solid var(--da-border);
  border-radius: 7px;
  background: var(--da-panel-2);
  color: var(--da-text);
  font-size: 12px;
  font-weight: 550;
  white-space: nowrap;
}

.da-btn:hover {
  border-color: var(--da-border-strong);
  background: var(--da-raised);
}

.da-btn.primary {
  border-color: color-mix(in srgb, var(--da-green) 50%, transparent);
  background: color-mix(in srgb, var(--da-green) 16%, var(--da-panel));
  color: color-mix(in srgb, var(--da-green) 75%, var(--da-text));
}

.da-btn.primary:hover {
  background: color-mix(in srgb, var(--da-green) 24%, var(--da-panel));
}

.da-btn.accent {
  border-color: color-mix(in srgb, var(--da-accent) 45%, transparent);
  background: var(--da-accent-soft);
  color: var(--da-accent);
}

.da-btn.ghost {
  border-color: transparent;
  background: transparent;
  color: var(--da-muted);
}

.da-btn.ghost:hover {
  background: var(--da-raised);
  color: var(--da-text);
}

.da-btn.small {
  height: 26px;
  padding: 0 8px;
  font-size: 11px;
  border-radius: 6px;
}

.da-btn.danger {
  color: var(--da-red);
  border-color: color-mix(in srgb, var(--da-red) 30%, transparent);
  background: var(--da-red-soft);
}

.da-kbd {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 5px;
  border: 1px solid var(--da-border-strong);
  border-bottom-width: 2px;
  border-radius: 4px;
  background: var(--da-panel);
  color: var(--da-muted);
  font-family: var(--da-code);
  font-size: 10px;
}

.da-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.da-left {
  display: flex;
  flex-direction: column;
  width: 252px;
  min-width: 210px;
  border-right: 1px solid var(--da-border);
  background: var(--da-panel);
  flex-shrink: 0;
}

.da-side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px 8px;
}

.da-section-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.9px;
  text-transform: uppercase;
  color: var(--da-muted);
}

.da-side-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.da-search {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0 10px 8px;
  padding: 0 8px;
  height: 30px;
  border: 1px solid var(--da-border);
  border-radius: 7px;
  background: var(--da-panel-2);
  color: var(--da-muted);
}

.da-search:focus-within {
  border-color: color-mix(in srgb, var(--da-accent) 55%, transparent);
  box-shadow: 0 0 0 2px var(--da-accent-soft);
}

.da-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 12px;
  color: var(--da-text);
}

.da-schema-select {
  margin: 0 10px 8px;
  width: calc(100% - 20px);
  height: 28px;
  border: 1px solid var(--da-border);
  border-radius: 6px;
  background: var(--da-panel-2);
  padding: 0 8px;
  font-size: 11px;
  color: var(--da-text-2);
}

.da-tree {
  flex: 1;
  overflow: auto;
  padding: 2px 6px 12px;
}

.da-tree-empty {
  padding: 18px 12px;
  color: var(--da-muted);
  font-size: 12px;
  text-align: center;
}

.da-table-node {
  margin-bottom: 1px;
}

.da-table-row {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-height: 29px;
  padding: 4px 6px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  text-align: left;
  color: var(--da-text-2);
}

.da-table-row:hover {
  background: var(--da-panel-2);
  color: var(--da-text);
}

.da-table-row.selected {
  background: var(--da-accent-soft);
  border-color: color-mix(in srgb, var(--da-accent) 22%, transparent);
  color: var(--da-text);
}

.da-table-row .da-chevron {
  color: var(--da-faint);
  transition: transform 0.15s ease;
  flex-shrink: 0;
}

.da-table-row.open .da-chevron {
  transform: rotate(90deg);
}

.da-table-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-family: var(--da-code);
}

.da-table-kind {
  font-size: 9px;
  color: var(--da-faint);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.da-columns {
  margin: 1px 0 4px 19px;
  padding: 2px 0 4px 9px;
  border-left: 1px solid var(--da-border);
}

.da-column {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-height: 24px;
  padding: 2px 5px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  text-align: left;
  color: var(--da-muted);
  font-family: var(--da-code);
  font-size: 11px;
}

.da-column:hover {
  color: var(--da-text);
  background: var(--da-panel-2);
}

.da-column .da-col-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.da-column .da-col-type {
  color: var(--da-faint);
  font-size: 9px;
  max-width: 78px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.da-pk {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 14px;
  padding: 0 4px;
  border-radius: 3px;
  background: var(--da-amber-soft);
  color: var(--da-amber);
  font-size: 9px;
  font-family: var(--da-ui);
  font-weight: 700;
}

.da-left-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 10px;
  border-top: 1px solid var(--da-border);
  color: var(--da-faint);
  font-size: 10px;
}

.da-center {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  background: var(--da-bg);
}

.da-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
  min-height: 37px;
  padding: 4px 8px 0;
  border-bottom: 1px solid var(--da-border);
  background: var(--da-panel);
  overflow-x: auto;
  scrollbar-width: none;
  flex-shrink: 0;
}

.da-tabs::-webkit-scrollbar {
  display: none;
}

.da-tab {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 32px;
  padding: 0 8px 0 11px;
  border: 1px solid transparent;
  border-bottom: 0;
  border-radius: 7px 7px 0 0;
  background: transparent;
  color: var(--da-muted);
  font-size: 12px;
  white-space: nowrap;
  max-width: 220px;
}

.da-tab:hover {
  background: var(--da-panel-2);
  color: var(--da-text-2);
}

.da-tab.active {
  background: var(--da-bg);
  border-color: var(--da-border);
  color: var(--da-text);
  position: relative;
}

.da-tab.active::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 1px;
  background: var(--da-bg);
}

.da-tab-label {
  overflow: hidden;
  text-overflow: ellipsis;
}

.da-tab-close {
  display: grid;
  place-items: center;
  width: 17px;
  height: 17px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--da-faint);
}

.da-tab-close:hover {
  background: var(--da-raised);
  color: var(--da-text);
}

.da-tab-add {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  margin-left: 2px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--da-muted);
  flex-shrink: 0;
}

.da-tab-add:hover {
  background: var(--da-panel-2);
  color: var(--da-text);
}

.da-editor-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-bottom: 1px solid var(--da-border);
  background: var(--da-panel-2);
  flex-shrink: 0;
}

.da-toolbar-group {
  display: flex;
  align-items: center;
  gap: 4px;
}

.da-toolbar-sep {
  width: 1px;
  height: 20px;
  background: var(--da-border);
  margin: 0 3px;
}

.da-editor-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
  color: var(--da-faint);
  font-size: 11px;
}

.da-readonly {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 22px;
  padding: 0 7px;
  border: 1px solid color-mix(in srgb, var(--da-green) 32%, transparent);
  border-radius: 5px;
  background: var(--da-green-soft);
  color: var(--da-green);
  font-size: 10px;
  font-weight: 650;
}

.da-editor-shell {
  display: flex;
  flex: 1;
  min-height: 170px;
  max-height: 45%;
  overflow: hidden;
  background: var(--da-bg);
  position: relative;
}

.da-gutter {
  width: 46px;
  padding: 12px 8px 20px 0;
  overflow: hidden;
  border-right: 1px solid color-mix(in srgb, var(--da-border) 55%, transparent);
  color: var(--da-faint);
  font-family: var(--da-code);
  font-size: 12px;
  line-height: 20px;
  text-align: right;
  user-select: none;
  flex-shrink: 0;
  background: var(--da-panel);
}

.da-gutter-inner {
  white-space: pre;
}

.da-editor {
  flex: 1;
  width: 100%;
  min-height: 100%;
  resize: none;
  border: 0;
  outline: 0;
  padding: 12px 16px 80px;
  background: transparent;
  color: var(--da-text);
  caret-color: var(--da-accent);
  font-family: var(--da-code);
  font-size: 12.5px;
  line-height: 20px;
  tab-size: 2;
  white-space: pre;
  overflow: auto;
}

.da-editor::placeholder {
  color: var(--da-faint);
}

.da-results {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  border-top: 1px solid var(--da-border);
  background: var(--da-panel);
}

.da-result-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
  min-height: 36px;
  padding: 0 10px;
  border-bottom: 1px solid var(--da-border);
  background: var(--da-panel-2);
  flex-shrink: 0;
}

.da-rtab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 35px;
  padding: 0 9px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--da-muted);
  font-size: 12px;
  font-weight: 550;
}

.da-rtab:hover {
  color: var(--da-text);
}

.da-rtab.active {
  color: var(--da-text);
  border-bottom-color: var(--da-accent);
}

.da-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 17px;
  height: 16px;
  padding: 0 5px;
  border-radius: 8px;
  background: var(--da-raised);
  color: var(--da-muted);
  font-size: 9px;
  font-weight: 700;
}

.da-result-actions {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
}

.da-result-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 11px;
  min-height: 27px;
  border-bottom: 1px solid var(--da-border);
  background: var(--da-panel);
  color: var(--da-faint);
  font-size: 10.5px;
  flex-shrink: 0;
}

.da-result-meta strong {
  color: var(--da-text-2);
  font-weight: 600;
}

.da-grid-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.da-grid {
  width: 100%;
  min-width: max-content;
  border-collapse: separate;
  border-spacing: 0;
  font-family: var(--da-code);
  font-size: 11.5px;
  table-layout: auto;
}

.da-grid th {
  position: sticky;
  top: 0;
  z-index: 2;
  min-width: 90px;
  max-width: 360px;
  padding: 6px 10px;
  border-right: 1px solid var(--da-border);
  border-bottom: 1px solid var(--da-border-strong);
  background: var(--da-panel-2);
  color: var(--da-text-2);
  text-align: left;
  font-family: var(--da-ui);
  font-size: 10.5px;
  font-weight: 650;
  white-space: nowrap;
}

.da-grid th .da-th-name {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
}

.da-grid th .da-th-type {
  display: block;
  margin-top: 1px;
  color: var(--da-faint);
  font-family: var(--da-code);
  font-size: 8.5px;
  font-weight: 400;
  text-transform: lowercase;
}

.da-grid td {
  max-width: 360px;
  padding: 5px 10px;
  border-right: 1px solid color-mix(in srgb, var(--da-border) 65%, transparent);
  border-bottom: 1px solid color-mix(in srgb, var(--da-border) 55%, transparent);
  color: var(--da-text-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: top;
}

.da-grid tr:nth-child(even) td {
  background: color-mix(in srgb, var(--da-panel-2) 45%, transparent);
}

.da-grid tr:hover td {
  background: var(--da-accent-soft);
}

.da-grid td.num {
  text-align: right;
  color: var(--da-cyan);
}

.da-grid td.null {
  color: var(--da-faint);
  font-style: italic;
}

.da-grid td.json {
  color: var(--da-purple);
}

.da-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 100%;
  min-height: 140px;
  padding: 24px;
  color: var(--da-muted);
  text-align: center;
}

.da-empty-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 1px solid var(--da-border);
  border-radius: 12px;
  background: var(--da-panel-2);
  color: var(--da-faint);
}

.da-empty-title {
  color: var(--da-text-2);
  font-size: 13px;
  font-weight: 600;
}

.da-empty-text {
  max-width: 420px;
  font-size: 11.5px;
  line-height: 1.6;
}

.da-plan-view {
  padding: 12px 14px 40px;
  overflow: auto;
  flex: 1;
}

.da-plan-summary {
  padding: 10px 12px;
  border: 1px solid var(--da-border);
  border-radius: 8px;
  background: var(--da-panel-2);
  color: var(--da-text-2);
  font-family: var(--da-code);
  font-size: 11.5px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.da-plan-raw {
  margin-top: 10px;
  border: 1px solid var(--da-border);
  border-radius: 8px;
  background: var(--da-bg);
  overflow: hidden;
}

.da-plan-raw summary {
  padding: 8px 12px;
  color: var(--da-muted);
  font-size: 11px;
  cursor: pointer;
  user-select: none;
}

.da-plan-raw pre {
  margin: 0;
  padding: 12px;
  max-height: 320px;
  overflow: auto;
  border-top: 1px solid var(--da-border);
  color: var(--da-text-2);
  font-family: var(--da-code);
  font-size: 11px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.da-insights {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-top: 10px;
}

.da-insight {
  display: flex;
  gap: 9px;
  padding: 9px 10px;
  border: 1px solid var(--da-border);
  border-radius: 8px;
  background: var(--da-panel);
}

.da-insight .da-sev-dot {
  width: 7px;
  height: 7px;
  margin-top: 5px;
  border-radius: 50%;
  flex-shrink: 0;
}

.da-insight.high .da-sev-dot { background: var(--da-red); }
.da-insight.medium .da-sev-dot { background: var(--da-amber); }
.da-insight.low .da-sev-dot { background: var(--da-accent); }
.da-insight.info .da-sev-dot { background: var(--da-muted); }

.da-insight-title {
  color: var(--da-text);
  font-size: 12px;
  font-weight: 600;
}

.da-insight-detail {
  margin-top: 2px;
  color: var(--da-muted);
  font-size: 11px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.da-right {
  display: flex;
  flex-direction: column;
  width: 348px;
  min-width: 290px;
  border-left: 1px solid var(--da-border);
  background: var(--da-panel);
  flex-shrink: 0;
}

.da-right-tabs {
  display: flex;
  align-items: center;
  gap: 0;
  min-height: 39px;
  padding: 0 8px;
  border-bottom: 1px solid var(--da-border);
  background: var(--da-panel-2);
  flex-shrink: 0;
}

.da-right-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 10px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--da-muted);
  font-size: 11.5px;
  font-weight: 550;
}

.da-right-tab:hover {
  color: var(--da-text);
}

.da-right-tab.active {
  color: var(--da-text);
  border-bottom-color: var(--da-accent);
}

.da-right-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.da-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px;
}

.da-assistant-intro {
  padding: 14px;
  border: 1px solid var(--da-border);
  border-radius: 10px;
  background:
    radial-gradient(120% 120% at 0% 0%, var(--da-purple-soft), transparent 60%),
    var(--da-panel-2);
}

.da-assistant-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--da-text);
  font-size: 13px;
  font-weight: 650;
}

.da-assistant-title .da-spark {
  color: var(--da-purple);
}

.da-assistant-text {
  margin-top: 7px;
  color: var(--da-muted);
  font-size: 11.5px;
  line-height: 1.65;
}

.da-assistant-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 11px;
}

.da-section {
  margin-top: 14px;
}

.da-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 7px;
}

.da-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--da-text-2);
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0.2px;
}

.da-finding {
  display: flex;
  gap: 8px;
  padding: 9px 10px;
  margin-bottom: 6px;
  border: 1px solid var(--da-border);
  border-radius: 8px;
  background: var(--da-panel-2);
}

.da-finding-main {
  flex: 1;
  min-width: 0;
}

.da-finding-title {
  color: var(--da-text);
  font-size: 11.5px;
  font-weight: 600;
}

.da-finding-detail {
  margin-top: 3px;
  color: var(--da-muted);
  font-size: 10.5px;
  line-height: 1.55;
}

.da-finding-suggest {
  margin-top: 5px;
  padding-left: 7px;
  border-left: 2px solid color-mix(in srgb, var(--da-accent) 45%, transparent);
  color: var(--da-text-2);
  font-size: 10.5px;
  line-height: 1.55;
}

.da-sev {
  display: inline-flex;
  align-items: center;
  height: 17px;
  padding: 0 5px;
  border-radius: 4px;
  font-size: 9px;
  font-weight: 750;
  letter-spacing: 0.3px;
  flex-shrink: 0;
}

.da-sev.high { color: var(--da-red); background: var(--da-red-soft); }
.da-sev.medium { color: var(--da-amber); background: var(--da-amber-soft); }
.da-sev.low { color: var(--da-accent); background: var(--da-accent-soft); }
.da-sev.info { color: var(--da-muted); background: var(--da-raised); }

.da-code-box {
  position: relative;
  margin-top: 7px;
  border: 1px solid var(--da-border);
  border-radius: 8px;
  background: var(--da-bg);
}

.da-code-box textarea {
  display: block;
  width: 100%;
  min-height: 96px;
  resize: vertical;
  border: 0;
  outline: 0;
  padding: 10px;
  background: transparent;
  color: var(--da-text);
  font-family: var(--da-code);
  font-size: 11px;
  line-height: 1.55;
}

.da-code-box .da-code-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 5px;
  padding: 5px 7px;
  border-top: 1px solid var(--da-border);
}

.da-prompt {
  margin-top: 7px;
  border: 1px solid var(--da-border);
  border-radius: 8px;
  background: var(--da-bg);
}

.da-prompt summary {
  padding: 8px 10px;
  color: var(--da-muted);
  font-size: 10.5px;
  cursor: pointer;
}

.da-prompt pre {
  margin: 0;
  padding: 0 10px 10px;
  max-height: 260px;
  overflow: auto;
  color: var(--da-text-2);
  font-family: var(--da-code);
  font-size: 10px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.da-compare-result {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 6px;
  margin-top: 8px;
}

.da-metric {
  padding: 8px;
  border: 1px solid var(--da-border);
  border-radius: 7px;
  background: var(--da-panel-2);
}

.da-metric-label {
  color: var(--da-faint);
  font-size: 9px;
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.da-metric-value {
  margin-top: 3px;
  color: var(--da-text);
  font-size: 13px;
  font-weight: 650;
}

.da-metric-value.good { color: var(--da-green); }
.da-metric-value.bad { color: var(--da-red); }

.da-list-item {
  display: block;
  width: 100%;
  padding: 9px 10px;
  margin-bottom: 6px;
  border: 1px solid var(--da-border);
  border-radius: 8px;
  background: var(--da-panel-2);
  text-align: left;
}

.da-list-item:hover {
  border-color: var(--da-border-strong);
  background: var(--da-raised);
}

.da-list-top {
  display: flex;
  align-items: center;
  gap: 7px;
}

.da-list-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--da-text);
  font-size: 11.5px;
  font-weight: 600;
}

.da-list-sub {
  margin-top: 4px;
  color: var(--da-muted);
  font-size: 10.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.da-list-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 5px;
  color: var(--da-faint);
  font-size: 9.5px;
}

.da-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 16px;
  padding: 0 5px;
  border-radius: 4px;
  font-size: 9px;
  font-weight: 650;
  background: var(--da-raised);
  color: var(--da-muted);
}

.da-status.ok { color: var(--da-green); background: var(--da-green-soft); }
.da-status.error { color: var(--da-red); background: var(--da-red-soft); }
.da-status.warn { color: var(--da-amber); background: var(--da-amber-soft); }

.da-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
}

.da-tag {
  display: inline-flex;
  align-items: center;
  height: 17px;
  padding: 0 5px;
  border-radius: 4px;
  background: var(--da-raised);
  color: var(--da-muted);
  font-size: 9.5px;
}

.da-kb-card {
  padding: 11px;
  margin-bottom: 8px;
  border: 1px solid var(--da-border);
  border-radius: 9px;
  background: var(--da-panel-2);
}

.da-kb-card:hover {
  border-color: var(--da-border-strong);
}

.da-kb-title {
  color: var(--da-text);
  font-size: 12px;
  font-weight: 650;
  line-height: 1.45;
}

.da-kb-problem {
  margin-top: 5px;
  color: var(--da-muted);
  font-size: 10.5px;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.da-kb-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 8px;
}

.da-kb-detail {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--da-border);
}

.da-kb-section-label {
  margin-top: 8px;
  color: var(--da-faint);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.6px;
  text-transform: uppercase;
}

.da-kb-text {
  margin-top: 3px;
  color: var(--da-text-2);
  font-size: 10.5px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.da-kb-sql {
  margin-top: 4px;
  padding: 7px;
  border: 1px solid var(--da-border);
  border-radius: 6px;
  background: var(--da-bg);
  color: var(--da-text-2);
  font-family: var(--da-code);
  font-size: 10px;
  line-height: 1.5;
  max-height: 150px;
  overflow: auto;
  white-space: pre-wrap;
}

.da-sidebar-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 25px;
  padding: 0 10px;
  border-top: 1px solid var(--da-border);
  background: var(--da-panel-2);
  color: var(--da-faint);
  font-size: 10px;
  flex-shrink: 0;
}

.da-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--da-green);
}

.da-status-dot.busy {
  background: var(--da-amber);
  animation: da-pulse 1.1s ease-in-out infinite;
}

.da-status-dot.off {
  background: var(--da-faint);
}

@keyframes da-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}

.da-spin {
  animation: da-rotate 0.8s linear infinite;
}

@keyframes da-rotate {
  to { transform: rotate(360deg); }
}

.da-toast {
  position: absolute;
  z-index: 60;
  right: 14px;
  bottom: 36px;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  max-width: 380px;
  padding: 10px 12px;
  border: 1px solid var(--da-border-strong);
  border-radius: 9px;
  background: var(--da-raised);
  box-shadow: var(--da-shadow);
  color: var(--da-text);
  font-size: 11.5px;
  line-height: 1.5;
  animation: da-toast-in 0.18s ease-out;
}

.da-toast.error {
  border-color: color-mix(in srgb, var(--da-red) 45%, transparent);
  background: color-mix(in srgb, var(--da-red) 12%, var(--da-raised));
}

.da-toast.success {
  border-color: color-mix(in srgb, var(--da-green) 45%, transparent);
  background: color-mix(in srgb, var(--da-green) 10%, var(--da-raised));
}

@keyframes da-toast-in {
  from { transform: translateY(8px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.da-overlay {
  position: absolute;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(3, 7, 12, 0.55);
  backdrop-filter: blur(4px);
}

.da-modal {
  width: min(560px, 100%);
  max-height: 86%;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--da-border-strong);
  border-radius: 12px;
  background: var(--da-panel);
  box-shadow: var(--da-shadow);
  overflow: hidden;
}

.da-modal.wide {
  width: min(760px, 100%);
}

.da-modal-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 16px;
  border-bottom: 1px solid var(--da-border);
}

.da-modal-title {
  flex: 1;
  font-size: 13px;
  font-weight: 650;
}

.da-modal-body {
  padding: 14px 16px;
  overflow: auto;
}

.da-modal-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 7px;
  padding: 11px 16px;
  border-top: 1px solid var(--da-border);
  background: var(--da-panel-2);
}

.da-field {
  margin-bottom: 11px;
}

.da-field:last-child {
  margin-bottom: 0;
}

.da-label {
  display: block;
  margin-bottom: 5px;
  color: var(--da-text-2);
  font-size: 10.5px;
  font-weight: 600;
}

.da-input,
.da-select,
.da-textarea {
  width: 100%;
  border: 1px solid var(--da-border);
  border-radius: 7px;
  outline: 0;
  background: var(--da-panel-2);
  color: var(--da-text);
  font-size: 12px;
}

.da-input,
.da-select {
  height: 32px;
  padding: 0 9px;
}

.da-textarea {
  min-height: 76px;
  padding: 8px 9px;
  resize: vertical;
  font-family: var(--da-code);
  font-size: 11px;
  line-height: 1.55;
}

.da-input:focus,
.da-select:focus,
.da-textarea:focus {
  border-color: color-mix(in srgb, var(--da-accent) 55%, transparent);
  box-shadow: 0 0 0 2px var(--da-accent-soft);
}

.da-form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.da-hint {
  margin-top: 4px;
  color: var(--da-faint);
  font-size: 10px;
  line-height: 1.5;
}

.da-error-box {
  margin-bottom: 10px;
  padding: 9px 10px;
  border: 1px solid color-mix(in srgb, var(--da-red) 35%, transparent);
  border-radius: 7px;
  background: var(--da-red-soft);
  color: var(--da-red);
  font-size: 11px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.da-warn-box {
  margin-bottom: 10px;
  padding: 9px 10px;
  border: 1px solid color-mix(in srgb, var(--da-amber) 35%, transparent);
  border-radius: 7px;
  background: var(--da-amber-soft);
  color: var(--da-amber);
  font-size: 11px;
  line-height: 1.55;
}

.da-muted {
  color: var(--da-muted);
}

.da-mono {
  font-family: var(--da-code);
}

.da-hidden {
  display: none !important;
}

@media (min-width: 1181px) {
  .da-right:not(.open) {
    display: none;
  }
}

@media (max-width: 1180px) {
  .da-right {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    z-index: 30;
    width: min(370px, 92%);
    box-shadow: -18px 0 45px rgba(0, 0, 0, 0.32);
    display: none;
  }

  .da-right.open {
    display: flex;
  }
}

@media (max-width: 860px) {
  .da-left {
    width: 210px;
    min-width: 180px;
  }

  .da-brand-sub {
    display: none;
  }

  .da-editor-shell {
    max-height: 42%;
  }
}

@media (max-width: 680px) {
  .da-left {
    display: none;
  }

  .da-header {
    padding: 0 8px;
    gap: 7px;
  }

  .da-ds-select {
    max-width: 150px;
  }

  .da-ds-select select {
    max-width: 100px;
  }

  .da-brand-name {
    font-size: 12px;
  }

  .da-toolbar-label {
    display: none;
  }
}
`;
