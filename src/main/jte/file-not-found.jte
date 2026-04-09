@import java.util.Map

@param Map<String, String> data

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="/css/prism-tomorrow.min.css">
    <title>File Not Found - ${data.get("filename")}</title>
</head>
<body class="p-8">
<h1>File Not Found</h1>
<p>The requested markdown file "${data.get("filename")}" was not found.</p>
<p>Would you like to create it?</p>
<form action="/createNewMarkdown" method="post">
    <input type="hidden" name="filename" value="${data.get("filename")}">
    <button type="submit" class="bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded">
        Yes, create the file
    </button>
</form>
<a href="/" class="text-blue-500 hover:text-blue-700">No, return to home</a>

<script src="/js/prism-bundle.js"></script>
</body>
</html>