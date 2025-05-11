@import java.util.Map

@param String markdownDirectory

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <script defer src="https://cdn.jsdelivr.net/npm/alpinejs@3.x.x/dist/cdn.min.js"></script>
    <script src="https://unpkg.com/htmx.org@1.9.12"></script>
    <script src="https://unpkg.com/htmx.org/dist/ext/json-enc.js"></script>
    <title>Configuration</title>
</head>
<body class="p-8" x-data="{ showNewDataSourceForm: false, newDataSourceName: '' }">
<h1>Base64 Utils</h1>

<div class="form-group">
    <h3>Base64 Encode</h3>
    <form hx-post="/base64" hx-ext="json-enc" hx-target="#result">
    <label for="encodedText">Encoded Text:</label>
    <textarea id="encodedText" name="encodedText" class="config-input"></textarea>
    <label for="decodeToFile">Decode To File:</label>
    <input type="text" id="decodeToFile" name="decodeToFile" class="config-input">
    <button type="submit" class="btn-blue-glow">
        <i class="fas fa-save"></i>
    </button>
    </form>
    <div id="result" class="mt-4">
    </div>
</div>

</body>
</html>

