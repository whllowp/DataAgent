import React from 'react';
import { SqlStudio } from './SqlStudio.jsx';
import { DataAgentIcon } from './icons.jsx';
import { installDataAgentObservability } from './observability.js';

const NS = 'dataagent';
const PANEL_ID = 'dataagent';

export const inject = ['slots', 'locale'];

export function apply(ctx) {
  ctx.effect(() => ctx.locale.register(NS, {
    zh: {
      panel: 'DataAgent SQL',
      description: 'DataAgent SQL 工作台、端云可观测与桌面小宠物：本地边缘 + 云端根因定位。',
    },
    en: {
      panel: 'DataAgent SQL',
      description: 'Cloud read-only SQL workspace, history, team knowledge base, and DSH optimization entry point.',
    },
  }), 'dataagent: dictionaries');

  const t = ctx.locale.bind(NS);
  const Panel = () => <SqlStudio embedded />;

  installDataAgentObservability(ctx);

  ctx.slots.inject('main', () => ctx.slots.register({
    name: 'main',
    key: PANEL_ID,
    locale: NS,
  }, Panel));

  ctx.slots.inject('sidebar.panellist', () => ctx.slots.register({
    name: 'sidebar.panellist',
    id: PANEL_ID,
    order: 20,
    label: () => t('panel'),
    locale: NS,
  }, ({ size }) => <DataAgentIcon size={size || 18} />));
}
