@param String markdownFile
@param String title
@param boolean editMode
@param String directoryName

<!DOCTYPE html>
<html lang="en">
    <head>
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/easymde/dist/easymde.min.css">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/themes/prism-tomorrow.min.css">
        <link rel="stylesheet" type="text/css" href="/css/style.css">
        <style>
            [x-cloak] {
                display: none !important;
            }
        </style>
        <script src="https://unpkg.com/htmx.org@1.9.12"></script>
        <script src="https://unpkg.com/htmx.org@1.9.12/dist/ext/json-enc.js"></script>
        <script defer src="https://cdn.jsdelivr.net/npm/alpinejs@3.x.x/dist/cdn.min.js"></script>
        <title>${title}</title>
    </head>
    <body class="p-8" x-data="{ editMode: ${editMode} }">
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
                hx-get="/markdownViewer?filename=${java.net.URLEncoder.encode(markdownFile, java.nio.charset.StandardCharsets.UTF_8)}">
        </button>
        <button hidden id="hiddenEditButton"
                hx-trigger="click"
                hx-target="#markdownEditor"
                hx-swap="outerHTML"
                hx-select="#markdownEditor"
                hx-get="/markdownEditor?filename=${java.net.URLEncoder.encode(markdownFile, java.nio.charset.StandardCharsets.UTF_8)}">
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
        <!-- JavaScript -->
        <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/prism.min.js"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-python.min.js"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-javascript.min.js"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-java.min.js"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-sql.min.js"></script>
        <script src="https://cdn.jsdelivr.net/npm/easymde/dist/easymde.min.js"></script>
        <script src="/js/paste-image.js"></script>
        <script src="/js/markdown.js"></script>
        <script>
            if (${editMode}) {
                document.addEventListener('DOMContentLoaded', function() {
                    htmx.trigger('#hiddenEditButton', 'click');
                });
            } else {
                document.addEventListener('DOMContentLoaded', function() {
                    htmx.trigger('#hiddenViewButton', 'click');
                });
            }
        </script>
        <script type="module">
            import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs';

            mermaid.initialize({
                startOnLoad: false
            });
            window.mermaid = mermaid;
        </script>
    </body>
</html>