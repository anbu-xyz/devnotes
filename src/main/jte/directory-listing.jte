@import java.util.List
@import uk.anbu.devnotes.controller.DirectoryListingController.FileEntry

@param String directoryName
@param String parentDirectoryName
@param String fileSystemPath
@param List<FileEntry> entries
@param String title

<!DOCTYPE html>
<html lang="en">
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" type="text/css" href="/css/directory-listing.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <title>${title}</title>
</head>
<body class="p-8">
@if(directoryName.isEmpty())
    <h1>
        <i class="fas fa-home" onclick="writeToClipboard('${fileSystemPath}')"></i>
    </h1>
@else
    <h1>${directoryName}/ <i class="fas fa-copy" onclick="writeToClipboard('${fileSystemPath}')"></i></h1>
@endif

<div class="mt-8">
    <button onclick="createMarkdown()" class="btn-blue-glow" title="New Markdown">
        <i class="fas fa-file-alt tool-button"></i>
    </button>
    <button onclick="createSubdirectory()" class="btn-blue-glow" title="New Folder">
        <i class="fas fa-folder-plus tool-button"></i>
    </button>
    |
    <button onclick="searchSubdirectory('${directoryName}')" class="btn-blue-glow" title="Search Folder">
        <i class="fas fa-search tool-button"></i>
    </button>
    <button onclick="recentlyModified()" class="btn-blue-glow" title="Recently Modified Files">
        <i class="fas fa-history tool-button"></i>
    </button>
    |
    <button onclick="document.getElementById('file-upload').click()" class="btn-blue-glow" title="Upload File">
        <i class="fas fa-upload tool-button"></i>
    </button>
    <input type="file" id="file-upload" style="display: none;" onchange="uploadFile(this.files[0])">
    |
    <button class="btn-blue-glow" title="Tools">
        <a href="/tools">
             <i class="fas fa-tools tool-button"></i>
        </a>
    </button>
</div>

<ul class="directory-list">
    @if(directoryName.length() > 1)
        <li>
            <a href="/markdown?filename=${parentDirectoryName}" class="text-blue-500 hover:text-blue-700">
                <i class="fas fa-level-up-alt mr-2"></i>..
            </a>
        </li>
    @endif
    @for(var entry : entries)
        <li class="entry-container">
            <div class="entry-main">
                <a href="/markdown?filename=${directoryName}/${entry.filename()}" class="text-blue-500 hover:text-blue-700 entry-link" data-entry="${entry.filename()}">
                    @if(entry.isDirectory())
                        <i class="fas fa-folder mr-2 text-yellow-500"></i>
                    @endif
                    ${entry.filename()}
                </a>
                <span class="entry-timestamp text-gray-500 text-sm">
                    ${entry.lastModified()}
                </span>
            </div>
            <div class="entry-actions" id="actions-${entry.filename()}">
                @if(entry.fileType().equals("puml"))
                    <button onclick="renderPlantUml('${entry.filename()}')">
                        <i class="fas fa-draw-polygon"></i>Render
                    </button>
                    <hr>
                @endif
                <button onclick="renameEntry('${entry.filename()}')">
                    <i class="fas fa-edit"></i>Rename
                </button>
                @if(!entry.isDirectory())
                    <button onclick="copyFileEntry('${entry.filename()}')">
                        <i class="fas fa-copy"></i>Make a Copy
                    </button>
                @endif
                <button onclick="deleteEntry('${entry.filename()}')">
                    <i class="fas fa-trash-alt"></i>Delete
                </button>
            </div>
        </li>
    @endfor
</ul>

<div id="paste-area">
    Paste image here
</div>
<div hidden id="dl-current-directory-name">${directoryName}</div>

<dialog id="search-dialog">
    <form id="search-form">
        <h3>Search in <span id="search-dialog-path"></span></h3>
        <div class="search-dialog-field">
            <label for="search-query">Search text</label>
            <input type="text" id="search-query" name="q" autocomplete="off">
        </div>
        <div class="search-dialog-options">
            <label>
                <input type="checkbox" id="search-case-sensitive">
                Case sensitive
            </label>
            <label>
                <input type="checkbox" id="search-regex">
                Regular expression
            </label>
        </div>
        <div class="search-dialog-buttons">
            <button type="submit" class="btn-blue-glow">
                <i class="fas fa-search"></i> Search
            </button>
            <button type="button" id="search-cancel" class="btn-blue-glow">Cancel</button>
        </div>
    </form>
</dialog>

<script src="/js/paste-image.js"></script>
<script src="/js/directory-listing.js"></script>
</body>
</html>