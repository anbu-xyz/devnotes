@param String initialContent
@param String initialRenderMode
<!DOCTYPE html>
<html lang="en">
<head>
    <link rel="stylesheet" type="text/css" href="/css/render.css">
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Groovy Playground</title>
    <style>
        /* Two-column split layout */
        html, body { height: 100%; margin: 0; }
        body { font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial; }
        .container { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; height: 100vh; padding: 1rem; box-sizing: border-box; }
        .panel { background: #0f1724; color: #e6edf3; border-radius: 6px; padding: 0.75rem; box-sizing: border-box; display: flex; flex-direction: column; }
        .panel .header { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; margin-bottom: 0.5rem; flex-wrap: wrap; }
        .panel .title { font-weight: 600; font-size: 0.95rem; }
        textarea#editor { width: 100%; resize: none; background: #0b1220; color: #dbeafe; border: 1px solid #233044; padding: 0.75rem; font-family: menlo, Monaco, 'Courier New', monospace; font-size: 0.95rem; border-radius: 4px; box-sizing: border-box; flex: 1 1 auto; }
        #preview { overflow: auto; flex: 1 1 auto; padding: 0.5rem; }
        .controls { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; }
        button { background: #2563eb; color: white; border: none; padding: 0.45rem 0.65rem; border-radius: 4px; cursor: pointer; font-size: 0.85rem; }
        button.secondary { background: #374151; }
        button:disabled { opacity: 0.4; cursor: not-allowed; }
        .error { margin-top: 0.5rem; color: #fecaca; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, monospace; font-size: 0.85rem; }
        #saveStatus { font-size: 0.78rem; color: #86efac; opacity: 0; transition: opacity 0.4s ease; white-space: nowrap; }
        #saveStatus.error { color: #fca5a5; }
        #runStatus { font-size: 0.78rem; color: #93c5fd; opacity: 0; transition: opacity 0.4s ease; white-space: nowrap; }
        #runStatus.error { color: #fca5a5; }
        #saveAsRow { display: none; gap: 0.5rem; align-items: center; margin-bottom: 0.4rem; }
        #saveAsRow input { flex: 1; background: #0b1220; color: #dbeafe; border: 1px solid #3b5270; padding: 0.3rem 0.6rem; border-radius: 4px; font-size: 0.88rem; font-family: menlo, Monaco, 'Courier New', monospace; }
        #saveAsRow input:focus { outline: none; border-color: #2563eb; }
        select#renderMode { background: #1e3a5f; color: #dbeafe; border: 1px solid #3b5270; padding: 0.35rem 0.5rem; border-radius: 4px; font-size: 0.85rem; cursor: pointer; }
        select#renderMode:focus { outline: none; border-color: #2563eb; }
        label.mode-label { color: #9ca3af; font-size: 0.82rem; white-space: nowrap; }
        /* Preview output styles */
        #preview pre { background: #1e293b; color: #e2e8f0; padding: 0.75rem; border-radius: 4px; overflow-x: auto; font-family: menlo, Monaco, 'Courier New', monospace; font-size: 0.85rem; margin: 0.25rem 0; }
        #preview table { border-collapse: collapse; width: 100%; font-size: 0.85rem; }
        #preview th { background: #1e3a5f; color: #dbeafe; border: 1px solid #374151; padding: 0.4rem 0.75rem; text-align: left; }
        #preview td { background: #0b1a2e; color: #e6edf3; border: 1px solid #374151; padding: 0.4rem 0.75rem; }
        #preview tr:hover td { background: #112240; }
        #preview .groovy-text-output { background: #1e293b; color: #e2e8f0; padding: 0.75rem; border-radius: 4px; white-space: pre-wrap; font-family: menlo, Monaco, 'Courier New', monospace; font-size: 0.85rem; margin: 0.25rem 0; }
        #preview p { color: #e6edf3; margin: 0.5rem 0; }
        #preview h1, #preview h2, #preview h3, #preview h4 { color: #93c5fd; margin: 0.5rem 0; }
        #preview a { color: #60a5fa; }
        #busy-overlay { display: none; position: absolute; inset: 0; background: rgba(15,23,36,0.6); border-radius: 6px; align-items: center; justify-content: center; color: #93c5fd; font-size: 0.9rem; }
        #right { position: relative; }
        @media (max-width: 800px) {
            .container { grid-template-columns: 1fr; }
        }
    </style>
</head>
<body>
<div class="container">
    <div class="panel" id="left">
        <div class="header">
            <div class="controls">
                <label class="mode-label" for="renderMode">Mode:</label>
                <select id="renderMode">
                    <option value="text">text</option>
                    <option value="html">html</option>
                    <option value="code-block">code-block</option>
                    <option value="csv-table">csv-table</option>
                    <option value="csv-table-with-header">csv-table-with-header</option>
                </select>
            </div>
            <div class="controls">
                <button id="saveBtn" class="secondary" disabled title="No save location set — use Save as… first">Save</button>
                <button id="saveAsBtn" class="secondary">Save as…</button>
            </div>
            <div class="controls">
                <span id="saveStatus" aria-live="polite"></span>
                <button id="runBtn">▶ Run</button>
                <button id="resetBtn" class="secondary">Reset</button>
            </div>
        </div>
        <div id="saveAsRow">
            <input type="text" id="saveAsInput" placeholder="Relative path, e.g. scripts/my-script.groovy" aria-label="Save path" />
            <button id="saveAsConfirmBtn">Save here</button>
            <button id="saveAsCancelBtn" class="secondary">Cancel</button>
        </div>
        <textarea id="editor" aria-label="Groovy editor" spellcheck="false">${initialContent}</textarea>
        <div class="error" id="error" role="status" aria-live="polite" style="display:none"></div>
    </div>

    <div class="panel" id="right">
        <div class="header">
            <div class="title">Output</div>
            <div class="controls">
                <span id="runStatus" aria-live="polite"></span>
            </div>
        </div>
        <div id="preview" aria-live="polite"></div>
        <div id="busy-overlay">Running…</div>
    </div>
</div>

<script>
    const editor = document.getElementById('editor');
    const preview = document.getElementById('preview');
    const errorBox = document.getElementById('error');
    const runBtn = document.getElementById('runBtn');
    const resetBtn = document.getElementById('resetBtn');
    const renderMode = document.getElementById('renderMode');
    const saveStatus = document.getElementById('saveStatus');
    const runStatus = document.getElementById('runStatus');
    const saveBtn = document.getElementById('saveBtn');
    const saveAsBtn = document.getElementById('saveAsBtn');
    const saveAsRow = document.getElementById('saveAsRow');
    const saveAsInput = document.getElementById('saveAsInput');
    const saveAsConfirmBtn = document.getElementById('saveAsConfirmBtn');
    const saveAsCancelBtn = document.getElementById('saveAsCancelBtn');
    const busyOverlay = document.getElementById('busy-overlay');

    const RENDER_MODE_KEY = 'groovyPlaygroundRenderMode';
    const SAVE_PATH_KEY = 'groovyPlaygroundSavePath';

    // ----- Restore render mode from localStorage -----
    const storedMode = localStorage.getItem(RENDER_MODE_KEY);
    const serverMode = '${initialRenderMode}';
    const effectiveMode = storedMode || serverMode || 'csv-table-with-header';
    renderMode.value = effectiveMode;

    renderMode.addEventListener('change', () => {
        localStorage.setItem(RENDER_MODE_KEY, renderMode.value);
        debouncedRun();
        debouncedAutosave();
    });

    // ----- Debounce helper -----
    function debounce(fn, wait) {
        let t;
        return function (...args) {
            clearTimeout(t);
            t = setTimeout(() => fn.apply(this, args), wait);
        };
    }

    // ----- Status helpers -----
    function showSaveStatus(msg, isError = false) {
        saveStatus.textContent = msg;
        saveStatus.className = isError ? 'error' : '';
        saveStatus.style.opacity = '1';
        clearTimeout(saveStatus._fadeTimer);
        saveStatus._fadeTimer = setTimeout(() => { saveStatus.style.opacity = '0'; }, 2000);
    }

    function showRunStatus(msg, isError = false) {
        runStatus.textContent = msg;
        runStatus.className = isError ? 'error' : '';
        runStatus.style.opacity = '1';
        clearTimeout(runStatus._fadeTimer);
        runStatus._fadeTimer = setTimeout(() => { runStatus.style.opacity = '0'; }, 3000);
    }

    function showError(msg) {
        if (!msg) {
            errorBox.style.display = 'none';
            errorBox.textContent = '';
        } else {
            errorBox.style.display = 'block';
            errorBox.textContent = msg;
        }
    }

    // ----- Autosave -----
    const debouncedAutosave = debounce(async () => {
        try {
            const res = await fetch('/groovy-playground/autosave', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ script: editor.value, renderMode: renderMode.value })
            });
            showSaveStatus(res.ok ? 'Autosaved' : 'Autosave failed', !res.ok);
        } catch (e) {
            showSaveStatus('Autosave failed', true);
        }
    }, 800);

    // ----- Execute -----
    async function runScript() {
        showError(null);
        busyOverlay.style.display = 'flex';
        runBtn.disabled = true;
        try {
            const res = await fetch('/groovy-playground/execute', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ script: editor.value, renderMode: renderMode.value })
            });
            const html = await res.text();
            preview.innerHTML = html;
            if (!res.ok) {
                showRunStatus('Error', true);
            }
        } catch (e) {
            showError('Request failed: ' + e.message);
            showRunStatus('Error', true);
        } finally {
            busyOverlay.style.display = 'none';
            runBtn.disabled = false;
        }
    }

    const debouncedRun = debounce(runScript, 800);

    editor.addEventListener('input', () => {
        debouncedRun();
        debouncedAutosave();
    });

    editor.addEventListener('keydown', (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
            e.preventDefault();
            runScript();
        }
    });

    runBtn.addEventListener('click', runScript);

    // ----- Reset -----
    const DEFAULT_SCRIPT = `def output = "N, N squared\\n"
for (int i = 0; i < 10; i++) {
    output += i + "," + (i * i) + "\\n"
}
output`;

    resetBtn.addEventListener('click', () => {
        editor.value = DEFAULT_SCRIPT;
        renderMode.value = 'csv-table-with-header';
        localStorage.setItem(RENDER_MODE_KEY, 'csv-table-with-header');
        runScript();
        debouncedAutosave();
    });

    // ----- Save to path -----
    let currentSavePath = localStorage.getItem(SAVE_PATH_KEY) || null;

    function updateSaveButton() {
        saveBtn.disabled = !currentSavePath;
        saveBtn.title = currentSavePath
            ? 'Save to: ' + currentSavePath
            : 'No save location set — use Save as… first';
    }
    updateSaveButton();

    async function saveToPath(path) {
        try {
            const res = await fetch('/groovy-playground/save?savePath=' + encodeURIComponent(path), {
                method: 'POST',
                headers: { 'Content-Type': 'text/plain' },
                body: editor.value
            });
            if (res.ok) {
                currentSavePath = path;
                localStorage.setItem(SAVE_PATH_KEY, path);
                updateSaveButton();
                showSaveStatus('Saved to ' + path);
            } else {
                showSaveStatus('Save failed', true);
            }
        } catch (e) {
            showSaveStatus('Save failed', true);
        }
    }

    saveBtn.addEventListener('click', () => { if (currentSavePath) saveToPath(currentSavePath); });

    saveAsBtn.addEventListener('click', () => {
        saveAsInput.value = currentSavePath || '';
        saveAsRow.style.display = 'flex';
        saveAsInput.focus();
    });

    saveAsCancelBtn.addEventListener('click', () => { saveAsRow.style.display = 'none'; });

    saveAsConfirmBtn.addEventListener('click', () => {
        const path = saveAsInput.value.trim();
        if (!path) return;
        saveAsRow.style.display = 'none';
        saveToPath(path);
    });

    saveAsInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') saveAsConfirmBtn.click();
        if (e.key === 'Escape') saveAsCancelBtn.click();
    });

    // ----- Initial run -----
    runScript();
</script>
</body>
</html>

