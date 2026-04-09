@param String groovyCodeContent
@param String title

<!DOCTYPE html>
<html lang="en">
<head>
    <link rel="stylesheet" type="text/css" href="/css/render.css">
    <link rel="stylesheet" href="/css/prism-tomorrow.min.css">

    <script src="/js/prism-bundle.js"></script>

    <title>${title}</title>
    <style>
        #playgroundBtn { margin-bottom: 1rem; padding: 0.35rem 0.8rem; border: none; border-radius: 4px; cursor: pointer; font-size: 0.9rem; background: #374151; color: white; }
    </style>
</head>
<body class="p-8">
<h1>${title}</h1>

<button id="playgroundBtn" data-filename="${title}">Playground</button>

<pre><code class="language-groovy">$unsafe{groovyCodeContent}</code></pre>

<script>Prism.highlightAll();</script>

<script>
    const source = document.querySelector('pre code').textContent;
    const filename = document.getElementById('playgroundBtn').dataset.filename;

    document.getElementById('playgroundBtn').addEventListener('click', async () => {
        try {
            await fetch('/groovy-playground/autosave', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ script: source, renderMode: 'text' })
            });
        } catch (e) { /* navigate anyway */ }
        localStorage.setItem('groovyPlaygroundSavePath', filename);
        window.location.href = '/groovy-playground';
    });
</script>

</body>
</html>