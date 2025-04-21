@param String htmlContent
@param String markdownFile
@param boolean editMode

<!DOCTYPE html>
<html>
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
    <title>${markdownFile.replaceAll(".md$", "")}</title>
</head>
<body class="p-8">
<div x-cloak x-data="{ editMode: ${editMode} }">
<div id="viewContent" x-show="!editMode">$unsafe{htmlContent}</div>
<div id="editContent" x-show="editMode">
</div>
<button id="editButton" @click="editMode = true" class="btn-blue-glow" x-show="!editMode"
        hx-get="/fetchRawMarkdown?filename=${java.net.URLEncoder.encode(markdownFile, java.nio.charset.StandardCharsets.UTF_8)}"
        hx-target="#editContent"
        hx-trigger="click"
        hx-select="textarea"
        hx-swap="innerHTML">
    <i class="fas fa-edit"></i>Edit
</button>
<button id="saveButton" @click="editMode = false; saveContent()" class="btn-blue-glow" x-show="editMode">
    <i class="fas fa-save"></i>Save
</button>
<!-- JavaScript -->
<script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/prism.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-python.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-javascript.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-java.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/easymde/dist/easymde.min.js"></script>
<script src="/js/edit.js"></script>
<script>
    var easyMDE;
    function downloadExcel(outputFileName, markdownFileName) {
        window.location.href = '/downloadExcel?outputFileName=' + outputFileName + '&markdownFileName=' + markdownFileName;
    }

    function h1IsAbsent() {
        return document.getElementById('viewContent').querySelector('h1') == null;
    }

    document.addEventListener('htmx:afterRequest', function(evt) {
        easyMDE = new EasyMDE({ element: document.querySelector("#editor") });
    })

    document.addEventListener('DOMContentLoaded', function() {
        const viewContent = document.getElementById('viewContent');
        const h1 = viewContent.querySelector('h1');

        if (h1) {
            // Create home link
            const folderLink = document.createElement('a');
            folderLink.href = 'renderDirectoryContents?directoryName=' +
                encodeURIComponent('${markdownFile}'.split('/').slice(0, -1).join('/'));

            folderLink.classList.add('text-blue-500', 'hover:text-blue-700', 'mr-2');
            folderLink.innerHTML = '<i class="fas fa-folder-open" style="padding-right: 0.2em"></i>';

            // Create edit link
            const editLink = document.createElement('a');
            editLink.href = '#';
            editLink.classList.add('text-blue-500', 'hover:text-blue-700');
            editLink.innerHTML = '<i class="fas fa-edit" style="padding-left: 0.2em"></i>';
            editLink.onclick = function(e) {
                e.preventDefault();
                document.getElementById('editButton').click();
            };

            h1.insertBefore(folderLink, h1.firstChild);
            h1.appendChild(editLink);

            // hide the edit button
            document.getElementById('editButton').style.display = 'none';
        }
    });

    document.addEventListener('keydown', function(e) {
        if ((e.ctrlKey || e.metaKey) && e.key === 's') {
            e.preventDefault(); // Prevent the browser's save dialog

            // If save button is visible.
            if (document.querySelector('#saveButton').style.display !== 'none') {
                document.getElementById('saveButton').click();
            }
        }
    });
</script>
</div>
</body>
</html>