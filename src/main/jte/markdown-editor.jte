@param String originalMarkdown
@param String title

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
                @click="await saveContent(); editMode = false; htmx.trigger('#hiddenViewButton', 'click')"
                class="btn-blue-glow">
            <i class="fas fa-save"></i>Save
        </button>
        <label class="checkbox-label">
            <input type="checkbox" id="autoSaveCheckbox" checked>
            <span class="checkbox-label-text">Auto Save</span>
        </label>
    </div>
</body>
</html>
