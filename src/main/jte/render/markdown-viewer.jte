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
        <hr>
        <p><em>Last modified: <span id="md-last-modified-time-viewer">${lastModifiedTime}</span></em></p>
        <span hidden id="md-file-path">${markdownFile}</span>
        <script>
            if (window.mermaid) {
                window.mermaid.run({
                    querySelector: '.mermaid'
                });
                document.querySelectorAll('.mermaid').forEach((element) => {
                    element.style.display = 'block';
                });
            }
        </script>
    </div>
</body>
</html>
