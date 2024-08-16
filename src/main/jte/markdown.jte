@import java.util.Map

@param Map<String, String> data

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/simplemde/latest/simplemde.min.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/themes/prism-tomorrow.min.css">
    <link rel="stylesheet" type="text/css" href="/css/style.css">
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
</body>
</html>