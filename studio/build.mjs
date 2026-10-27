import * as esbuild from 'esbuild';
import { mkdir, copyFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..');
const staticDir = resolve(root, 'backend/src/main/resources/static/studio');
const pluginDir = resolve(root, 'dsh-plugin');
const watch = process.argv.includes('--watch');

await mkdir(staticDir, { recursive: true });
await mkdir(pluginDir, { recursive: true });

const common = {
  bundle: true,
  platform: 'browser',
  target: ['chrome110'],
  jsx: 'transform',
  jsxFactory: 'React.createElement',
  jsxFragment: 'React.Fragment',
  legalComments: 'none',
  logLevel: 'info',
  define: {
    'process.env.NODE_ENV': '"production"',
  },
};

async function buildStandalone() {
  await esbuild.build({
    ...common,
    entryPoints: [resolve(here, 'src/main.jsx')],
    outfile: resolve(staticDir, 'app.js'),
    format: 'iife',
    minify: true,
    sourcemap: false,
  });
  await copyFile(resolve(here, 'index.html'), resolve(staticDir, 'index.html'));
  await copyFile(resolve(pluginDir, 'icon.svg'), resolve(staticDir, 'icon.svg'));
}

async function buildDshPlugin() {
  const result = await esbuild.build({
    ...common,
    entryPoints: [resolve(here, 'src/dsh-entry.jsx')],
    outfile: resolve(here, 'dsh-entry.bundle.cjs'),
    format: 'cjs',
    external: ['react'],
    minify: false,
    sourcemap: false,
    write: false,
  });
  const bundled = result.outputFiles[0].text;
  const wrapper = `window.__ModuleLoader__.load({
  id: '@local/dataagent-dsh',
  factory(require) {
    const module = { exports: {} };
    const exports = module.exports;
    (function (module, exports, require) {
${bundled}
    })(module, module.exports, require);
    return module.exports;
  }
});
`;
  await writeFile(resolve(pluginDir, 'client.js'), wrapper, 'utf8');
}

async function build() {
  await Promise.all([buildStandalone(), buildDshPlugin()]);
  console.log('Built studio page and DSH client bundle.');
}

await build();

if (watch) {
  const ctx = await esbuild.context({
    ...common,
    entryPoints: [resolve(here, 'src/main.jsx')],
    outfile: resolve(staticDir, 'app.js'),
    format: 'iife',
  });
  await ctx.watch();
  console.log('Watching studio UI...');
}
