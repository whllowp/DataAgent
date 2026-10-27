import React from 'react';

const paths = {
  database: (
    <>
      <ellipse cx="12" cy="5" rx="7.5" ry="3" />
      <path d="M4.5 5v7c0 1.66 3.36 3 7.5 3s7.5-1.34 7.5-3V5" />
      <path d="M4.5 12v7c0 1.66 3.36 3 7.5 3s7.5-1.34 7.5-3v-7" />
    </>
  ),
  table: (
    <>
      <rect x="3.5" y="4.5" width="17" height="15" rx="2" />
      <path d="M3.5 9.5h17M9.5 9.5v10M15.5 9.5v10" />
    </>
  ),
  column: (
    <>
      <path d="M5 5.5h14M5 12h14M5 18.5h9" />
      <circle cx="19" cy="18.5" r="1.6" />
    </>
  ),
  play: <path d="M8 5.5v13l10-6.5z" fill="currentColor" stroke="none" />,
  plan: (
    <>
      <rect x="3.5" y="4" width="7" height="5" rx="1.2" />
      <rect x="13.5" y="15" width="7" height="5" rx="1.2" />
      <path d="M7 9v3.5a2 2 0 0 0 2 2h4.5M17 15V9" />
      <path d="M14.5 11.5 17 9l2.5 2.5" />
    </>
  ),
  sparkles: (
    <>
      <path d="M12 3.5l1.6 4.4 4.4 1.6-4.4 1.6L12 15.5l-1.6-4.4L6 9.5l4.4-1.6z" />
      <path d="M18.5 14.5l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z" />
    </>
  ),
  format: (
    <>
      <path d="M4 6h16M8 10h12M4 14h16M8 18h9" />
    </>
  ),
  plus: <path d="M12 5v14M5 12h14" />,
  close: <path d="M6 6l12 12M18 6L6 18" />,
  history: (
    <>
      <path d="M4 5.5v5h5" />
      <path d="M4.6 10.5A8 8 0 1 1 6 17.3" />
      <path d="M12 7.5V12l3 2" />
    </>
  ),
  book: (
    <>
      <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v15.5H6.8A2.8 2.8 0 0 0 4 21.3z" />
      <path d="M4 18.2A2.8 2.8 0 0 1 6.8 15.5H20" />
      <path d="M8 7h8M8 10.5h6" />
    </>
  ),
  settings: (
    <>
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-1.9 1.9-.06-.06a1.7 1.7 0 0 0-1.88-.34 1.7 1.7 0 0 0-1 1.56V20h-2.7v-.1a1.7 1.7 0 0 0-1-1.56 1.7 1.7 0 0 0-1.88.34l-.06.06-1.9-1.9.06-.06A1.7 1.7 0 0 0 8 15a1.7 1.7 0 0 0-1.56-1H6v-2.7h.44A1.7 1.7 0 0 0 8 10a1.7 1.7 0 0 0-.34-1.88l-.06-.06 1.9-1.9.06.06A1.7 1.7 0 0 0 11.44 6a1.7 1.7 0 0 0 1-1.56V4h2.7v.44a1.7 1.7 0 0 0 1 1.56 1.7 1.7 0 0 0 1.88-.34l.06-.06 1.9 1.9-.06.06A1.7 1.7 0 0 0 19.6 9.5a1.7 1.7 0 0 0 1.56 1H22v2.7h-.44a1.7 1.7 0 0 0-1.56 1z" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4.5 4.5" />
    </>
  ),
  refresh: (
    <>
      <path d="M20 5.5v5h-5" />
      <path d="M19.4 10.5A7.5 7.5 0 1 0 20 15" />
    </>
  ),
  download: (
    <>
      <path d="M12 4v11M7.5 10.5 12 15l4.5-4.5" />
      <path d="M4 19.5h16" />
    </>
  ),
  copy: (
    <>
      <rect x="8" y="8" width="11" height="11" rx="2" />
      <path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2" />
    </>
  ),
  chevron: <path d="m9 5 7 7-7 7" />,
  check: <path d="m4.5 12.5 5 5L20 6.5" />,
  warning: (
    <>
      <path d="M12 3.5 21 20H3z" />
      <path d="M12 9v5M12 17.2v.1" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7v5.2l3.4 2" />
    </>
  ),
  key: (
    <>
      <circle cx="8" cy="15.5" r="3.5" />
      <path d="m11 13 8-8M16 5l3 3M14 7l3 3" />
    </>
  ),
  terminal: (
    <>
      <rect x="3.5" y="4.5" width="17" height="15" rx="2" />
      <path d="m7 9 3 3-3 3M13 15h4" />
    </>
  ),
  external: (
    <>
      <path d="M14 4h6v6M20 4l-8 8" />
      <path d="M19 14v5a1.5 1.5 0 0 1-1.5 1.5h-12A1.5 1.5 0 0 1 4 19V7a1.5 1.5 0 0 1 1.5-1.5H10" />
    </>
  ),
  trash: (
    <>
      <path d="M4.5 7h15M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13" />
      <path d="M10 11v5M14 11v5" />
    </>
  ),
  send: <path d="M4 4.5 21 12 4 19.5 6.5 12z" />,
  moon: <path d="M20 14.5A8.5 8.5 0 1 1 9.5 4 7 7 0 0 0 20 14.5z" />,
  sun: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.2 5.2l1.4 1.4M17.4 17.4l1.4 1.4M18.8 5.2l-1.4 1.4M6.6 17.4l-1.4 1.4" />
    </>
  ),
  lock: (
    <>
      <rect x="5" y="10.5" width="14" height="10" rx="2" />
      <path d="M8 10.5V8a4 4 0 0 1 8 0v2.5M12 14.5v2" />
    </>
  ),
  chart: (
    <>
      <path d="M4 20V4M4 20h16" />
      <path d="M8 16v-4M12.5 16V8M17 16v-6" />
    </>
  ),
  panel: (
    <>
      <rect x="3.5" y="4.5" width="17" height="15" rx="2" />
      <path d="M15 4.5v15" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 11v5M12 8v.1" />
    </>
  ),
  arrowUpRight: (
    <>
      <path d="M7 17 17 7M9 7h8v8" />
    </>
  ),
};

export function Icon({ name, size = 15, className = '', strokeWidth = 1.8, title }) {
  const content = paths[name] || paths.info;
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden={title ? undefined : true}
      role={title ? 'img' : undefined}
    >
      {title ? <title>{title}</title> : null}
      {content}
    </svg>
  );
}

export function DataAgentIcon({ size = 18 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="2.5" y="3" width="19" height="18" rx="4" fill="url(#da-grad)" />
      <ellipse cx="12" cy="8" rx="5.5" ry="2.2" stroke="white" strokeWidth="1.5" />
      <path d="M6.5 8v5c0 1.2 2.46 2.2 5.5 2.2s5.5-1 5.5-2.2V8" stroke="white" strokeWidth="1.5" />
      <path d="M6.5 13v3.5c0 1.2 2.46 2.2 5.5 2.2s5.5-1 5.5-2.2V13" stroke="white" strokeWidth="1.5" />
      <defs>
        <linearGradient id="da-grad" x1="3" y1="3" x2="21" y2="21" gradientUnits="userSpaceOnUse">
          <stop stopColor="#2f81f7" />
          <stop offset="0.55" stopColor="#1f6feb" />
          <stop offset="1" stopColor="#8250df" />
        </linearGradient>
      </defs>
    </svg>
  );
}
