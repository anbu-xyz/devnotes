@param String directoryName
@param java.util.List<uk.anbu.devtools.controller.DirectoryListingController.FileEntry> entries

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <title>Directory Listing - ${directoryName}</title>
    <style>
    </style>
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
        <li class="entry-container">
            <a href="/markdown?filename=${directoryName}/${entry.filename()}" class="text-blue-500 hover:text-blue-700 entry-link" data-entry="${entry.filename()}">
                @if(entry.isDirectory())
                    <i class="fas fa-folder mr-2 text-yellow-500"></i>
                @endif
                ${entry.filename()}
            </a>
            <div class="entry-actions" id="actions-${entry.filename()}">
                <button onclick="renameEntry('${entry.filename()}')">Rename</button>
                <button onclick="deleteEntry('${entry.filename()}')">Delete</button>
            </div>
        </li>
    @endfor
</ul>

<div class="mt-8">
    <button onclick="createSubdirectory()" class="bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded mr-4">
        Create New Subdirectory
    </button>
    <button onclick="createMarkdown()" class="bg-green-500 hover:bg-green-700 text-white font-bold py-2 px-4 rounded">
        Create New Markdown
    </button>
</div>

<script>
    var currentDirectoryName = "${directoryName}";
</script>
<script src="/js/directory-listing.js"></script>
</body>
</html>