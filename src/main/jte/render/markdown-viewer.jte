@param String title
@param String htmlContent
@param String markdownFile
@param String lastModifiedTime
@param uk.anbu.devnotes.types.FrontMatter frontMatter
@param int mermaidBlockCount
@param int cacheFileCount

<!DOCTYPE html>
<html>
<head>
    <title>${title}</title>
</head>
<body>
    <!-- Note: only the div is used by the htmx to display the editor. Refer to "markdown.jte"
    for the rest of the code -->
    <div id="markdownViewer" x-cloak x-show="!editMode">
        @if(!frontMatter.isEmpty())
        <div class="fm-metadata">
            @if(frontMatter.tags() != null && !frontMatter.tags().isEmpty())
            <div class="fm-tags">
                @for(String tag : frontMatter.tags())
                <span class="fm-tag">${tag}</span>
                @endfor
            </div>
            @endif
            @if(frontMatter.description() != null)
            <p class="fm-description">${frontMatter.description()}</p>
            @endif
        </div>
        @endif
        $unsafe{htmlContent}
        @if(cacheFileCount > 0)
        <dialog id="cache-clear-dialog" class="cache-clear-dialog">
            <p class="cache-clear-dialog-message">Clear <strong>${cacheFileCount}</strong> cached output file${cacheFileCount == 1 ? "" : "s"} for this page?</p>
            <div class="cache-clear-dialog-actions">
                <button id="clearCacheCancel" class="cache-clear-cancel-btn">Cancel</button>
                <button id="clearCacheConfirm" class="cache-clear-confirm-btn">Clear</button>
            </div>
        </dialog>
        @endif
        <button id="editButton" @click="if (await acquireEditLock()) { editMode = true; htmx.trigger('#hiddenEditButton', 'click') } else { document.getElementById('edit-lock-dialog').showModal() }" class="btn-blue-glow">
            <i class="fas fa-edit"></i>Edit
        </button>
        <hr>
        <p><em>Last modified: <span id="md-last-modified-time-viewer">${lastModifiedTime}</span></em></p>
        @if(mermaidBlockCount > 0)
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
        @endif
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
            @if(cacheFileCount > 0)
            (function() {
                const h1 = document.querySelector('#markdownViewer h1');
                if (h1) {
                    const badge = document.createElement('button');
                    badge.className = 'cache-files-badge';
                    badge.title = 'This page has ${cacheFileCount} cached output file${cacheFileCount == 1 ? "" : "s"} \u2014 click to clear';
                    badge.innerHTML = '<i class="fas fa-database"></i>\u00a0${cacheFileCount}';
                    badge.addEventListener('click', function(e) {
                        e.preventDefault();
                        document.getElementById('cache-clear-dialog').showModal();
                    });
                    h1.appendChild(badge);
                }
                document.getElementById('clearCacheConfirm').addEventListener('click', async function() {
                    document.getElementById('cache-clear-dialog').close();
                    const mdFile = document.getElementById('md-file-path').textContent;
                    await fetch('/markdown/clear-cache?' + new URLSearchParams({ filename: mdFile }), { method: 'POST' });
                    htmx.trigger('#hiddenViewButton', 'click');
                });
                document.getElementById('clearCacheCancel').addEventListener('click', function() {
                    document.getElementById('cache-clear-dialog').close();
                });
            })();
            @endif
        </script>
    </div>
</body>
</html>
