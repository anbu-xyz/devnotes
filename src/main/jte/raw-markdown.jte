@param String originalMarkdown
@param String filename

<!DOCTYPE html>
<html>
<head>
    !{var title = filename.substring(filename.lastIndexOf('/')==-1?0:filename.lastIndexOf('/')+1);}
    !{var title_md = title.replaceAll(".md$", "");}
    <title>${title_md}: edit</title>
</head>
<body>
    <textarea id="editor">$unsafe{originalMarkdown.toString()}</textarea>
</body>
</html>
