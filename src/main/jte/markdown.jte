@param String htmlContent
@param java.nio.file.Path markdownFile
@param String originalMarkdown
@param boolean editMode

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/simplemde/latest/simplemde.min.css">
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
    <title>${markdownFile.toString().replaceAll(".md$", "")}</title>
</head>
<body class="p-8">
<div x-cloak x-data="{ editMode: ${editMode} }">
<div id="viewContent" x-show="!editMode">$unsafe{htmlContent}</div>
<div id="editContent" x-show="editMode">
    <textarea id="editor">$unsafe{originalMarkdown.toString()}</textarea>
</div>
<button id="editButton" x-on:click="editMode = true" class="btn-blue-glow" x-show="!editMode">
    <i class="fas fa-edit"></i>Edit
</button>
<button id="saveButton" x-on:click="editMode = false; saveContent()" class="btn-blue-glow" x-show="editMode">
    <i class="fas fa-save"></i>Save
</button>
<!-- JavaScript -->
<script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/prism.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-python.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-javascript.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-java.min.js"></script>
<script src="https://cdn.jsdelivr.net/simplemde/latest/simplemde.min.js"></script>
<script src="/js/edit.js"></script>
<script>
    function downloadExcel(outputFileName, markdownFileName) {
        window.location.href = '/downloadExcel?outputFileName=' + outputFileName + '&markdownFileName=' + markdownFileName;
    }
</script>
</div>
</body>
</html>