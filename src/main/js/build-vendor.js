'use strict';
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const projectRoot = path.resolve(__dirname, '..', '..', '..');
const staticDir   = path.join(projectRoot, 'src', 'main', 'resources', 'static');
const generatedDir = path.join(staticDir, 'generated');
const staticJs    = path.join(generatedDir, 'js');
const staticCss   = path.join(generatedDir, 'css');
const staticWf    = path.join(generatedDir, 'webfonts');
const nm          = path.join(__dirname, 'node_modules');
const esbuildBin  = path.join(nm, 'esbuild', 'bin', 'esbuild');

const WEBFONT_EXTS = new Set(['.eot', '.ttf', '.woff', '.woff2']);

// ── helpers ────────────────────────────────────────────────────────────────

function copy(src, dst) {
    fs.mkdirSync(path.dirname(dst), { recursive: true });
    fs.copyFileSync(src, dst);
    console.log('  copy  ' + path.relative(projectRoot, dst));
}

function bundle(args) {
    execFileSync(process.execPath, [esbuildBin, ...args], { stdio: 'inherit' });
    console.log('  built ' + path.relative(projectRoot, args.find(a => a.startsWith('--outfile=')).slice('--outfile='.length)));
}

function withTempFile(name, content, fn) {
    const tmpFile = path.join(__dirname, name);
    fs.writeFileSync(tmpFile, content);
    try { fn(tmpFile); } finally { fs.rmSync(tmpFile, { force: true }); }
}

// ── htmx ──────────────────────────────────────────────────────────────────
console.log('\nhtmx');
copy(path.join(nm, 'htmx.org', 'dist', 'htmx.min.js'),       path.join(staticJs, 'htmx.min.js'));
copy(path.join(nm, 'htmx.org', 'dist', 'ext', 'json-enc.js'), path.join(staticJs, 'htmx-json-enc.js'));

// ── Alpine.js ─────────────────────────────────────────────────────────────
console.log('\nalpinejs');
copy(path.join(nm, 'alpinejs', 'dist', 'cdn.min.js'), path.join(staticJs, 'alpine.min.js'));

// ── Font Awesome ──────────────────────────────────────────────────────────
console.log('\nfont-awesome');
copy(
    path.join(nm, '@fortawesome', 'fontawesome-free', 'css', 'all.min.css'),
    path.join(staticCss, 'font-awesome.min.css')
);
const wfSrc = path.join(nm, '@fortawesome', 'fontawesome-free', 'webfonts');
for (const f of fs.readdirSync(wfSrc)) {
    if (WEBFONT_EXTS.has(path.extname(f))) {
        copy(path.join(wfSrc, f), path.join(staticWf, f));
    }
}

// ── Prism CSS ─────────────────────────────────────────────────────────────
console.log('\nprism css');
copy(
    path.join(nm, 'prismjs', 'themes', 'prism-tomorrow.css'),
    path.join(staticCss, 'prism-tomorrow.min.css')
);

// ── Prism bundle (all languages used in the app) ──────────────────────────
console.log('\nprism bundle');
withTempFile('_prism-entry.js', [
    "require('prismjs');",
    "require('prismjs/components/prism-python');",
    "require('prismjs/components/prism-javascript');",
    "require('prismjs/components/prism-markup-templating');",
    "require('prismjs/components/prism-java');",
    "require('prismjs/components/prism-sql');",
    "require('prismjs/components/prism-groovy');",
    "require('prismjs/components/prism-yaml');",
    "require('prismjs/components/prism-mermaid');",
].join('\n'), (tmpFile) => {
    bundle([
        tmpFile,
        '--bundle', '--format=iife', '--global-name=__prism_ignore',
        '--minify',
        `--outfile=${path.join(staticJs, 'prism-bundle.js')}`,
    ]);
});

// ── Mermaid (global IIFE, exposes globalThis.mermaid) ─────────────────────
console.log('\nmermaid');
withTempFile('_mermaid-entry.mjs', [
    "import mermaid from 'mermaid';",
    "globalThis.mermaid = mermaid;",
].join('\n'), (tmpFile) => {
    bundle([
        tmpFile,
        '--bundle', '--format=iife', '--minify',
        `--outfile=${path.join(staticJs, 'mermaid.min.js')}`,
    ]);
});

console.log('\nVendor bundles built successfully.');

