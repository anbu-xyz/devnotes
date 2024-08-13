@param String markdownDirectory

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <title>Configuration</title>
</head>
<body class="p-8">
<h1>Configuration</h1>
<form action="/config" method="post">
    <label for="markdownDirectory">Markdown Directory:</label>
    <input type="text" id="markdownDirectory" name="markdownDirectory" value="${markdownDirectory}" class="w-full p-2 border rounded">
    <button type="submit" class="bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded mt-4">
        Save Configuration
    </button>
</form>
<a href="/" class="text-blue-500 hover:text-blue-700 mt-4 inline-block">Return to Home</a>
</body>
</html>