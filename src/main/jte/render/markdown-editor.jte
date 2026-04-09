@param String originalMarkdown
@param String title
@param String lastModifiedTime

<!DOCTYPE html>
<html>
<head>
    <title>${title} :EDITOR:</title>
</head>
<body>
    <!-- Note: only the text area is used by the htmx to display the editor. Refer to "markdown.jte"
    for the rest of the code -->
    <div id="markdownEditor" x-cloak x-show="editMode">
        <textarea id="cmInitialContent" hidden>$unsafe{originalMarkdown}</textarea>
        <div id="codeMirrorEditor"></div>
        <button id="saveButton"
                @click="await saveContent(); await releaseEditLock(); editMode = false; isSlides ? (window.location.href = '/markdown?filename=' + encodeURIComponent(document.getElementById('md-file-path').textContent)) : htmx.trigger('#hiddenViewButton', 'click')"
                class="btn-blue-glow">
            <i class="fas fa-save"></i>Save
        </button>
        <label class="checkbox-label">
            <input type="checkbox" id="autoSaveCheckbox" checked>
            <span class="checkbox-label-text">Auto Save</span>
        </label>
        <div class="editor-font-size-control" title="Editor font size">
            <button type="button" id="fontSizeDecrease" class="editor-font-size-btn" aria-label="Decrease font size">A-</button>
            <span id="editorFontSizeDisplay" class="editor-font-size-display">14px</span>
            <button type="button" id="fontSizeIncrease" class="editor-font-size-btn" aria-label="Increase font size">A+</button>
        </div>
        <span hidden id="md-last-modified-time-editor">${lastModifiedTime}</span>
    </div>
</body>
</html>
