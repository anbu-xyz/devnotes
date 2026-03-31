@param String mermaidCodeContent
@param String title

<!DOCTYPE html>
<html lang="en">
<head>
    <link rel="stylesheet" type="text/css" href="/css/render.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/themes/prism-tomorrow.min.css">

    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/prism.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-mermaid.min.js"></script>

    <title>${title}</title>
    <style>
        #toggleBtn, #playgroundBtn { margin-bottom: 1rem; padding: 0.35rem 0.8rem; border: none; border-radius: 4px; cursor: pointer; font-size: 0.9rem; }
        #toggleBtn { background: #2563eb; color: white; }
        #playgroundBtn { background: #374151; color: white; margin-left: 0.4rem; }
        #sourceView { display: none; }
        #diagramView svg { max-width: 100%; }
        #renderError { color: #dc2626; font-family: monospace; white-space: pre-wrap; }
    </style>
</head>
<body class="p-8">
<h1>${title}</h1>

<button id="toggleBtn">Source</button>
<button id="playgroundBtn" data-filename="${title}">Playground</button>

<div id="sourceView">
    <pre><code class="language-mermaid">$unsafe{mermaidCodeContent}</code></pre>
</div>
<div id="diagramView">
    <div id="diagram"></div>
    <div id="renderError"></div>
</div>

<script type="module">
    import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs';
    mermaid.initialize({ startOnLoad: false, theme: 'neutral' });

    const toggleBtn = document.getElementById('toggleBtn');
    const sourceView = document.getElementById('sourceView');
    const diagramView = document.getElementById('diagramView');
    const diagram = document.getElementById('diagram');
    const renderError = document.getElementById('renderError');
    const source = document.querySelector('#sourceView code').textContent;
    const playgroundBtn = document.getElementById('playgroundBtn');
    const filename = playgroundBtn.dataset.filename;

    let showingDiagram = true;
    let rendered = false;

    (async () => {
        diagram.innerHTML = '<div class="mermaid">' + source + '</div>';
        try {
            await mermaid.run({ querySelector: '#diagram .mermaid' });
            rendered = true;
        } catch (e) {
            renderError.textContent = e.str || e.message || String(e);
        }
    })();

    toggleBtn.addEventListener('click', async () => {
        if (!showingDiagram) {
            sourceView.style.display = 'none';
            diagramView.style.display = 'block';
            toggleBtn.textContent = 'Source';
            showingDiagram = true;
        } else {
            diagramView.style.display = 'none';
            sourceView.style.display = 'block';
            toggleBtn.textContent = 'Render';
            showingDiagram = false;
        }
    });

    playgroundBtn.addEventListener('click', async () => {
        try {
            await fetch('/mermaid-playground/autosave', {
                method: 'POST',
                headers: { 'Content-Type': 'text/plain' },
                body: source
            });
        } catch (e) { /* navigate anyway */ }
        localStorage.setItem('mermaidPlaygroundSavePath', filename);
        window.location.href = '/mermaid-playground';
    });
</script>

<script>Prism.highlightAll();</script>

</body>
</html>