@import java.util.Map

@param String markdownDirectory

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="/generated/css/font-awesome.min.css">
    <script defer src="/generated/js/alpine.min.js"></script>
    <script src="/generated/js/htmx.min.js"></script>
    <script src="/generated/js/htmx-json-enc.js"></script>
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

