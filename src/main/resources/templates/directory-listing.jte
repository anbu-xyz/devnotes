@param String directoryName
@param java.util.List<uk.anbu.devtools.controller.MarkdownController.FileEntry> entries

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <title>Directory Listing - ${directoryName}</title>
</head>
<body class="p-8">
<h1>Directory: ${directoryName}</h1>
<ul class="list-none">
    @if(directoryName.length() > 1)
        <li class="mb-2">
            <a href="/markdown?filename=${directoryName.substring(0, directoryName.lastIndexOf('/'))}" class="text-blue-500 hover:text-blue-700">
                <i class="fas fa-level-up-alt mr-2"></i>..
            </a>
        </li>
    @endif
    @for(var entry : entries)
        <li class="mb-2">
            <a href="/markdown?filename=${directoryName}/${entry.filename()}" class="text-blue-500 hover:text-blue-700">
                @if(entry.isDirectory())
                    <i class="fas fa-folder mr-2 text-yellow-500"></i>
<%--                @else--%>
<%--                    <i class="fas fa-file mr-2 text-gray-500"></i>--%>
                @endif
                ${entry.filename()}
            </a>
        </li>
    @endfor
</ul>
</body>
</html>