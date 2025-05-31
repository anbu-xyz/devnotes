@param String originalMarkdown
@param String title
@param String markdownFile

<!DOCTYPE html>
<html>
<head>
    <title>${title} :EDITOR:</title>
</head>
<body>
    <!-- Note: only the text area is used by the htmx to display the editor. Refer to "markdown.jte"
    for the rest of the code -->
    <div id="markdownEditor" x-cloak x-show="editMode">
        <textarea id="easyMdeEditor" style="display: none">$unsafe{originalMarkdown}</textarea>
        <button id="saveButton"
                @click="await saveContent(); editMode = false; htmx.trigger('#hiddenSaveButton', 'click')"
                class="btn-blue-glow">
            <i class="fas fa-save"></i>Save
        </button>
        <button style="display: none" id="hiddenSaveButton"
            hx-trigger="click"
            hx-target="#markdownViewer"
            hx-swap="outerHTML"
            hx-select="div#markdownViewer"
            hx-get="/markdownViewer?filename=${java.net.URLEncoder.encode(markdownFile, java.nio.charset.StandardCharsets.UTF_8)}">
        </button>
        <label class="checkbox-label">
            <input type="checkbox" id="autoSaveCheckbox" checked>
            <span class="checkbox-label-text">Auto Save</span>
        </label>
    </div>
</body>
</html>
