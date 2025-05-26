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
    <textarea id="editor">$unsafe{originalMarkdown.toString()}</textarea>
</body>
</html>
