@import java.util.List
@import uk.anbu.devnotes.controller.DirectoryListingController.FileEntry

@param String directoryName
@param List<FileEntry> entries

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
<ul class="directory-list">
    @if(directoryName.length() > 1)
        <li>
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
                <button onclick="renameEntry('${entry.filename()}')">
                    <i class="fas fa-edit"></i>Rename
                </button>
                <button onclick="deleteEntry('${entry.filename()}')">
                    <i class="fas fa-trash-alt"></i>Delete
                </button>
            </div>
        </li>
    @endfor
</ul>

<div class="mt-8">
    <button onclick="createSubdirectory()" class="btn-blue-glow">
        <i class="fas fa-folder-plus"></i>Create New Subdirectory
    </button>
    <button onclick="createMarkdown()" class="btn-blue-glow">
        <i class="fas fa-file-alt"></i>Create New Markdown
    </button>
    <button onclick="document.getElementById('file-upload').click()" class="btn-blue-glow">
        <i class="fas fa-upload"></i>Upload File
    </button>
    <input type="file" id="file-upload" style="display: none;" onchange="uploadFile(this.files[0])">
</div>

<div id="paste-area">
    Paste image here
</div>
<div>
    @if(directoryName.equals("."))
        <a href="/database" class="btn-blue-glow">
            <i class="fas fa-database"></i>Database Metadata
        </a>
        <a href="/config" class="btn-blue-glow">
            <i class="fas fa-cog"></i>Config Editor
        </a>
    @endif
</div>

<script>
    var currentDirectoryName = "${directoryName}";
</script>
<script src="/js/directory-listing.js"></script>
</body>
</html>