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
        <button id="editButton" @click="editMode = true" class="btn-blue-glow"
                hx-get="/markdownEditor?filename=${java.net.URLEncoder.encode(markdownFile, java.nio.charset.StandardCharsets.UTF_8)}"
                hx-target="#markdownEditor"
                hx-trigger="click"
                hx-swap="outerHTML"
                hx-select="#markdownEditor">
            <i class="fas fa-edit"></i>Edit
        </button>
        <hr>
        <p><em>Last modified: <span id="md-last-modified-time">${lastModifiedTime}</span></em></p>
        <p hidden id="md-file-path">${markdownFile}</p>
    </div>
</body>
</html>
