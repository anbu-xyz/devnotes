@param String markdownFile
@param String title
@param boolean editMode
@param boolean isSlides
@param String directoryName
@param java.util.Map<String, Object> urlQueryParams
<!DOCTYPE html>
<html lang="en">
    <head>
        <link rel="stylesheet" href="/css/font-awesome.min.css">
        <link rel="stylesheet" href="/css/prism-tomorrow.min.css">
        <link rel="stylesheet" type="text/css" href="/css/style.css">
        <style>
            [x-cloak] {
                display: none !important;
            }
        </style>
        <script src="/js/htmx.min.js"></script>
        <script src="/js/htmx-json-enc.js"></script>
        <script defer src="/js/alpine.min.js"></script>
        <title>${title}</title>
    </head>
        <body class="p-8" x-data="{ editMode: ${editMode}, isSlides: ${isSlides} }">
        <div id="markdownViewer" x-cloak x-show="!editMode">
            Loading...
        </div>
        <div id="markdownEditor" x-cloak x-show="editMode">
            Loading...
        </div>
        <p hidden id="md-file-path">${markdownFile}</p>
        <button hidden id="hiddenViewButton"
                hx-trigger="click"
                hx-target="#markdownViewer"
                hx-swap="outerHTML"
                hx-select="div#markdownViewer"
                hx-get="/markdownViewer?filename=${
                    java.net.URLEncoder.encode(markdownFile, java.nio.charset.StandardCharsets.UTF_8)
                }${
                    urlQueryParams == null || urlQueryParams.isEmpty() ? "" : '&' +
                    urlQueryParams.entrySet().stream()
                        .map(e -> java.net.URLEncoder.encode(e.getKey(), java.nio.charset.StandardCharsets.UTF_8)
                                + '=' + java.net.URLEncoder.encode(String.valueOf(e.getValue()), java.nio.charset.StandardCharsets.UTF_8))
                        .collect(java.util.stream.Collectors.joining("&"))
                }">
        </button>
        <button hidden id="hiddenEditButton"
                hx-trigger="click"
                hx-target="#markdownEditor"
                hx-swap="outerHTML"
                hx-select="#markdownEditor"
                hx-get="/markdownEditor?filename=${
                    java.net.URLEncoder.encode(markdownFile, java.nio.charset.StandardCharsets.UTF_8)
                }${
                    urlQueryParams == null || urlQueryParams.isEmpty() ? "" : '&' +
                    urlQueryParams.entrySet().stream()
                        .map(e -> java.net.URLEncoder.encode(e.getKey(), java.nio.charset.StandardCharsets.UTF_8)
                                + '=' + java.net.URLEncoder.encode(String.valueOf(e.getValue()), java.nio.charset.StandardCharsets.UTF_8))
                        .collect(java.util.stream.Collectors.joining("&"))
                }">
        </button>
        <div id="codeModal" class="modal-overlay">
            <div class="modal-dialog">
                <div class="modal-content">
                    <pre><code id="modalCode"></code></pre>
                </div>
            </div>
        </div>
        <div id="paste-area">
            Paste image here
        </div>
        <div hidden id="dl-current-directory-name">${directoryName}</div>
        <!-- Edit-lock dialog: shown when another session holds the edit lock -->
        <dialog id="edit-lock-dialog" class="edit-lock-dialog">
            <h2 class="edit-lock-dialog-title">&#128274; File Already Open for Editing</h2>
            <p class="edit-lock-dialog-body">
                This file is currently open for editing in another window or browser tab.
                Close that editor first, or wait - the lock expires automatically after 60 seconds of inactivity.
            </p>
            <div class="edit-lock-dialog-actions">
                <button class="edit-lock-force-btn"
                        onclick="document.getElementById('edit-lock-dialog').close(); openEditModeForce()">
                    Force Edit
                </button>
                <button class="btn-blue-glow"
                        onclick="document.getElementById('edit-lock-dialog').close()">
                    OK
                </button>
            </div>
        </dialog>
        <!-- JavaScript -->
        <script src="/js/prism-bundle.js"></script>
        <script type="module">
            import { basicSetup, EditorView, markdown, yaml, oneDark } from '/js/codemirror-bundle.js';

            window.initCodeMirrorEditor = function(mountId, initialContent) {
                const mountEl = document.getElementById(mountId);
                if (!mountEl) return;
                if (window.editorView) {
                    window.editorView.destroy();
                    window.editorView = null;
                }
                window.editorView = new EditorView({
                    doc: initialContent || '',
                    extensions: [
                        basicSetup,
                        markdown(),
                        oneDark,
                        EditorView.lineWrapping,
                    ],
                    parent: mountEl,
                });
            };
            window._cm6ready = true;
            window.dispatchEvent(new Event('cm6ready'));

            // Block-editor CM6 factory (separate from the main page editor)
            window._blockEditorView = null;
            window.initBlockEditorCM = function(mountEl, content, lang) {
                if (window._blockEditorView) {
                    window._blockEditorView.destroy();
                    window._blockEditorView = null;
                }
                const extensions = [basicSetup, oneDark, EditorView.lineWrapping];
                if (lang === 'yaml') extensions.push(yaml());
                // 'plain' → no language extension; just basicSetup
                window._blockEditorView = new EditorView({
                    doc: content || '',
                    extensions,
                    parent: mountEl,
                });
                return window._blockEditorView;
            };
        </script>
        <script src="/js/paste-image.js"></script>
        <!-- Block editor dialog: used for inline editing of data/groovy-exec blocks -->
        <dialog id="block-editor-dialog">
            <h3 class="block-editor-title">Edit Block</h3>
            <div id="block-editor-cm-mount"></div>
            <div class="block-editor-error" id="block-editor-error" style="display:none"></div>
            <div class="block-editor-actions">
                <button id="block-editor-cancel-btn" type="button">Cancel</button>
                <button id="block-editor-save-btn" type="button" class="btn-blue-glow">Save</button>
            </div>
        </dialog>
        <script src="/js/markdown.js"></script>
        <script>
            if (${editMode}) {
                document.addEventListener('DOMContentLoaded', async function() {
                    const acquired = await acquireEditLock();
                    if (!acquired) {
                        // Another session holds the lock - revert to view mode
                        const body = document.querySelector('[x-data]');
                        if (body && body._x_dataStack && body._x_dataStack[0]) {
                            body._x_dataStack[0].editMode = false;
                        }
                        setEditModeUrl(false);
                        document.getElementById('edit-lock-dialog').showModal();
                        htmx.trigger('#hiddenViewButton', 'click');
                    } else {
                        htmx.trigger('#hiddenEditButton', 'click');
                    }
                });
            } else {
                document.addEventListener('DOMContentLoaded', function() {
                    htmx.trigger('#hiddenViewButton', 'click');
                });
            }
        </script>
        <script src="/js/mermaid.min.js"></script>
        <script>
            // mermaid is available as a global from mermaid.min.js

            const MERMAID_THEME_KEY = 'mermaidTheme';
            const defaultTheme = 'dark';

            function applyTheme(theme) {
                mermaid.initialize({ startOnLoad: false, theme });
                window.mermaid = mermaid;
                window._mermaidTheme = theme;
            }

            window.setMermaidTheme = function(theme) {
                localStorage.setItem(MERMAID_THEME_KEY, theme);
                applyTheme(theme);
                document.querySelectorAll('.mermaid[data-source]').forEach(el => {
                    el.textContent = el.dataset.source;
                    el.removeAttribute('data-processed');
                });
                window.mermaid.run({ querySelector: '.mermaid' });
                document.querySelectorAll('.mermaid-theme-picker').forEach(sel => { sel.value = theme; });
            };

            applyTheme(localStorage.getItem(MERMAID_THEME_KEY) || defaultTheme);
        </script>
    </body>
</html>