@param String originalMarkdown
@param String title

<!DOCTYPE html>
<html>
<head>
    <title>${title} :EDITOR:</title>
</head>
<body>
    <textarea id="editor">$unsafe{originalMarkdown.toString()}</textarea>
</body>
</html>
