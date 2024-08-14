@param String markdownDirectory

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <title>Configuration</title>
</head>
<body class="p-8">
<h1>Configuration</h1>
<form action="/config" method="post" class="config-form">
    <div class="form-group">
        <label for="markdownDirectory">Markdown Directory:</label>
        <input type="text" id="markdownDirectory" name="markdownDirectory" value="${markdownDirectory}" class="config-input">
    </div>
    <button type="submit" class="create-button">
        <i class="fas fa-save"></i>Save Configuration
    </button>
</form>
<a href="/" class="create-button mt-4">
    <i class="fas fa-home"></i>Return to Home
</a>
</body>
</html>