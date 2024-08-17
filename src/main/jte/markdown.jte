@import java.util.Map

@param Map<String, String> data

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/simplemde/latest/simplemde.min.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/themes/prism-tomorrow.min.css">
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <script src="https://unpkg.com/htmx.org@1.9.1"></script>
    <title>${data.get("filename")}</title>
</head>
<body class="p-8">
<div id="viewContent">$unsafe{data.get("htmlContent")}</div>
<div id="editContent" style="display:none;">
    <textarea id="editor">$unsafe{data.get("originalMarkdown")}</textarea>
</div>
<button id="editButton" onclick="toggleEdit()" class="create-button">
    <i class="fas fa-edit"></i>Edit
</button>
<!-- JavaScript -->
<script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/prism.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-python.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-javascript.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-java.min.js"></script>
<script src="https://cdn.jsdelivr.net/simplemde/latest/simplemde.min.js"></script>
<script src="/js/edit.js"></script>
<script>
    function downloadExcel(fileName) {
        window.location.href = '/downloadExcel?fileName=' + fileName;
    }

    // HTMX will handle the re-execution of SQL
    document.body.addEventListener('htmx:afterSwap', function(evt) {
        if (evt.detail.target.classList.contains('sql-result')) {
            // Reinitialize sorting for the new table
            initializeSorting(evt.detail.target.querySelector('table.sortable'));
        }
    });

    // Add sorting functionality to tables
    document.addEventListener('DOMContentLoaded', function() {
        const tables = document.querySelectorAll('table.sortable');
        tables.forEach(initializeSorting);
    });

    function initializeSorting(table) {
        const headers = table.querySelectorAll('th');
        headers.forEach(header => {
            header.addEventListener('click', () => {
                const column = header.cellIndex;
                const dataType = header.getAttribute('data-type');
                sortTable(table, column, dataType);
            });
        });
    }

    function sortTable(table, column, dataType) {
        // Implement sorting logic based on data type
        // ...
    }
</script>
</body>
</html>