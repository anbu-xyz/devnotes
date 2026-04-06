@param String title
@param String htmlContent
@param String markdownFile
@param String lastModifiedTime

<!DOCTYPE html>
<html>
<head>
    <title>${title}</title>
</head>
<body>
    <!-- Note: only the div is used by the htmx to display the editor. Refer to "markdown.jte"
    for the rest of the code -->
    <div id="markdownViewer" x-cloak x-show="!editMode">
        $unsafe{htmlContent}
        <button id="editButton" @click="editMode = true; htmx.trigger('#hiddenEditButton', 'click')" class="btn-blue-glow">
            <i class="fas fa-edit"></i>Edit
        </button>
        <label style="display:inline-flex;align-items:center;gap:0.35rem;font-size:0.82rem;color:#6b7280;margin-left:0.75rem;">
            Mermaid:
            <select class="mermaid-theme-picker" onchange="window.setMermaidTheme && window.setMermaidTheme(this.value)"
                    style="font-size:0.82rem;padding:0.15rem 0.35rem;border-radius:3px;">
                <option value="default">Default</option>
                <option value="dark">Dark</option>
                <option value="neutral">Neutral</option>
                <option value="forest">Forest</option>
                <option value="base">Base</option>
            </select>
        </label>
        <hr>
        <p><em>Last modified: <span id="md-last-modified-time-viewer">${lastModifiedTime}</span></em></p>
        <span hidden id="md-file-path">${markdownFile}</span>
        <script>
            document.querySelectorAll('.mermaid').forEach(el => {
                el.dataset.source = el.textContent;
            });
            if (window.mermaid) {
                window.mermaid.run({
                    querySelector: '.mermaid'
                });
                document.querySelectorAll('.mermaid').forEach(el => { el.style.display = 'block'; });
            }
            document.querySelectorAll('.mermaid-theme-picker').forEach(sel => {
                sel.value = window._mermaidTheme || localStorage.getItem('mermaidTheme') || 'dark';
            });
        </script>
    </div>
</body>
</html>
