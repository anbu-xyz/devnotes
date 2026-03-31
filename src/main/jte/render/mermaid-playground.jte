@param String initialContent
<!DOCTYPE html>
<html lang="en">
<head>
    <link rel="stylesheet" type="text/css" href="/css/render.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/themes/prism-tomorrow.min.css">
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Mermaid playground</title>
    <style>
        /* Simple two-column split layout */
        html, body { height: 100%; margin: 0; }
        body { font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial; }
        .container { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; height: 100vh; padding: 1rem; box-sizing: border-box; }
        .panel { background: #0f1724; color: #e6edf3; border-radius: 6px; padding: 0.75rem; box-sizing: border-box; display: flex; flex-direction: column; }
        .panel .header { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; margin-bottom: 0.5rem; }
        .panel .title { font-weight: 600; font-size: 0.95rem; }
        textarea#editor { width: 100%; height: 100%; resize: none; background: #0b1220; color: #dbeafe; border: 1px solid #233044; padding: 0.75rem; font-family: menlo, Monaco, 'Courier New', monospace; font-size: 0.95rem; border-radius: 4px; box-sizing: border-box; flex: 1 1 auto; }
        /* Make the preview use all available vertical space and allow the diagram to stretch */
        #preview { overflow: auto; flex: 1 1 auto; display: flex; align-items: stretch; justify-content: center; padding: 0.5rem; }
        /* Ensure the mermaid wrapper fills the preview space */
        #preview .mermaid { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
        /* Make SVG fill available space in preview (may change aspect ratio) */
        #preview svg { width: 100%; height: 100%; max-width: none; }
        .controls { display: flex; gap: 0.5rem; align-items: center; }
        button { background: #2563eb; color: white; border: none; padding: 0.45rem 0.65rem; border-radius: 4px; cursor: pointer; }
        button.secondary { background: #374151; }
        .error { margin-top: 0.5rem; color: #fecaca; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, monospace; font-size: 0.85rem; }
        /* make SVG scale nicely inside preview - allow it to fill container height set earlier */
        #preview svg { max-width: 100%; }
        /* Full-screen modal for rendering the SVG */
        #fsModal .fs-overlay { position: fixed; inset: 0; background: rgba(2,6,23,0.85); display: flex; align-items: center; justify-content: center; z-index: 10000; opacity: 0; transition: opacity .15s ease; }
        #fsModal .fs-overlay.open { opacity: 1; }
        #fsModal .fs-content { position: relative; max-width: 95vw; max-height: 95vh; width: 95vw; height: 95vh; padding: 1rem; box-sizing: border-box; display: flex; align-items: center; justify-content: center; }
        #fsModal .fs-body { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; overflow: auto; }
        #fsModal svg { max-width: 100%; max-height: 100%; display: block; }
        #fsModal .fs-close { position: absolute; top: 8px; right: 8px; background: #111827; color: white; border: none; border-radius: 6px; width: 36px; height: 36px; font-size: 18px; cursor: pointer; }
        #saveStatus { font-size: 0.78rem; color: #86efac; opacity: 0; transition: opacity 0.4s ease; white-space: nowrap; }
        #saveStatus.error { color: #fca5a5; }
        @media (max-width: 800px) {
            .container { grid-template-columns: 1fr; }
        }
    </style>
</head>
<body>
<div class="container">
    <div class="panel" id="left">
        <div class="header">
            <div class="title">Mermaid editor</div>
            <div class="controls">
                <span id="saveStatus" aria-live="polite"></span>
                <button id="renderBtn">Render</button>
                <button id="fullscreenBtn" class="secondary">FullScreen</button>
                <button id="resetBtn" class="secondary">Reset</button>
            </div>
        </div>
        <textarea id="editor" aria-label="Mermaid editor">${initialContent}</textarea>
        <div class="error" id="error" role="status" aria-live="polite" style="display:none"></div>
    </div>

    <div class="panel" id="right">
        <div class="header">
            <div class="title">Preview</div>
            <div class="controls">
                <button id="downloadBtn" class="secondary">Download SVG</button>
            </div>
        </div>
        <div id="preview" aria-live="polite"></div>
    </div>
</div>

<script type="module">
    import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs';

    mermaid.initialize({ startOnLoad: false, theme: 'neutral' });

    const editor = document.getElementById('editor');
    const preview = document.getElementById('preview');
    const errorBox = document.getElementById('error');
    const renderBtn = document.getElementById('renderBtn');
    const resetBtn = document.getElementById('resetBtn');
    const downloadBtn = document.getElementById('downloadBtn');
    const fullscreenBtn = document.getElementById('fullscreenBtn');
    const saveStatus = document.getElementById('saveStatus');

    // Debounce helper
    function debounce(fn, wait) {
        let t;
        return function(...args) {
            clearTimeout(t);
            t = setTimeout(() => fn.apply(this, args), wait);
        };
    }

    // ----- Autosave -----
    function showSaveStatus(msg, isError = false) {
        saveStatus.textContent = msg;
        saveStatus.className = isError ? 'error' : '';
        saveStatus.style.opacity = '1';
        clearTimeout(saveStatus._fadeTimer);
        saveStatus._fadeTimer = setTimeout(() => { saveStatus.style.opacity = '0'; }, 2000);
    }

    const debouncedSave = debounce(async (text) => {
        try {
            const res = await fetch('/mermaid-playground/autosave', {
                method: 'POST',
                headers: { 'Content-Type': 'text/plain' },
                body: text
            });
            showSaveStatus(res.ok ? 'Saved' : 'Save failed', !res.ok);
        } catch (e) {
            showSaveStatus('Save failed', true);
        }
    }, 800);

    function showError(msg) {
        if (!msg) {
            errorBox.style.display = 'none';
            errorBox.textContent = '';
            return;
        }
        errorBox.style.display = 'block';
        errorBox.textContent = msg;
    }

    async function renderMermaid(source) {
        // Clear previous error
        showError(null);
        // Put raw mermaid code into a wrapper div that mermaid can find
        preview.innerHTML = '<div class="mermaid">' + source + '</div>';
        try {
            // mermaid.run will find .mermaid elements inside the preview and render them
            await mermaid.run({ querySelector: '#preview .mermaid' });
        } catch (err) {
            // If mermaid throws, show the error and keep the raw text
            showError(err && err.str ? (err.str + '\n' + (err.message || '')) : (err && err.message) || String(err));
        }
    }

    const debouncedRender = debounce(() => renderMermaid(editor.value), 300);

    // Live render on input
    editor.addEventListener('input', () => {
        debouncedRender();
        debouncedSave(editor.value);
    });

    // Manual render
    renderBtn.addEventListener('click', () => renderMermaid(editor.value));

    // Reset to sample content
    resetBtn.addEventListener('click', () => {
        editor.value = `graph TD\n    A[Client] --> B[Load Balancer]\n    B --> C[Server 1]\n    B --> D[Server 2]\n\n    click A "https://example.com" "Go to example"`;
        renderMermaid(editor.value);
        debouncedSave(editor.value);
    });

    // Download the current SVG if available
    downloadBtn.addEventListener('click', () => {
        const svg = preview.querySelector('svg');
        if (!svg) return alert('No SVG available to download');
        const serializer = new XMLSerializer();
        const svgStr = serializer.serializeToString(svg);
        const blob = new Blob([svgStr], { type: 'image/svg+xml;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'mermaid-diagram.svg';
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
    });

    // ----- Fullscreen modal helpers -----
    let _fsModal = null;
    function ensureFsModal() {
        if (_fsModal) return _fsModal;
        const wrapper = document.createElement('div');
        wrapper.id = 'fsModal';
        wrapper.innerHTML = `
            <div class="fs-overlay" role="dialog" aria-modal="true" aria-label="Mermaid full screen preview">
                <div class="fs-content">
                    <button class="fs-close" aria-label="Close full screen">&times;</button>
                    <div class="fs-body"></div>
                </div>
            </div>`;
        document.body.appendChild(wrapper);
        const overlay = wrapper.querySelector('.fs-overlay');
        const closeBtn = wrapper.querySelector('.fs-close');
        const body = wrapper.querySelector('.fs-body');

        overlay.addEventListener('click', (e) => { if (e.target === overlay) closeFsModal(); });
        closeBtn.addEventListener('click', closeFsModal);
        document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeFsModal(); });

        _fsModal = { wrapper, overlay, body };
        return _fsModal;
    }

    function openFsModalWithSvg(svg) {
        const { overlay, body } = ensureFsModal();
        body.innerHTML = '';
        const clone = svg.cloneNode(true);
        clone.style.maxWidth = '100%';
        clone.style.maxHeight = '100%';
        clone.style.display = 'block';
        clone.style.margin = '0 auto';
        body.appendChild(clone);
        overlay.classList.add('open');
    }

    function closeFsModal() {
        const modal = document.getElementById('fsModal');
        if (!modal) return;
        const overlay = modal.querySelector('.fs-overlay');
        if (overlay) overlay.classList.remove('open');
        // remove after a short delay to allow transition
        setTimeout(() => { const m = document.getElementById('fsModal'); if (m) m.remove(); _fsModal = null; }, 200);
    }

    // Fullscreen button logic: ensure diagram is rendered then open modal
    if (fullscreenBtn) {
        fullscreenBtn.addEventListener('click', async () => {
            await renderMermaid(editor.value);
            const svg = preview.querySelector('svg');
            if (!svg) return alert('No SVG available to show full screen');
            openFsModalWithSvg(svg);
        });
    }

    // Initial render
    renderMermaid(editor.value);
</script>
</body>
</html>

