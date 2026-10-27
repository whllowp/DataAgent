import React from 'react';
import { SqlStudio } from './SqlStudio.jsx';
import { DataAgentIcon } from './icons.jsx';

const NS = 'dataagent';
const PANEL_ID = 'dataagent';

export const inject = ['slots', 'locale'];

export function apply(ctx) {
  ctx.effect(() => ctx.locale.register(NS, {
    zh: {
      panel: 'DataAgent SQL',
      description: '云端只读 SQL 工作台、执行历史、团队知识库与 DSH 优化入口。',
    },
    en: {
      panel: 'DataAgent SQL',
      description: 'Cloud read-only SQL workspace, history, team knowledge base, and DSH optimization entry point.',
    },
  }), 'dataagent: dictionaries');

  const t = ctx.locale.bind(NS);
  const Panel = () => <SqlStudio embedded />;

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
