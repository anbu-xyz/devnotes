@import java.util.List
@import uk.anbu.devnotes.service.ImageAuditService.BrokenImageLink
@param List<String> orphanedImages
@param List<BrokenImageLink> brokenLinks

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <title>Image Audit</title>
</head>
<body class="p-8">

<h1><i class="fas fa-images"></i> Image Audit</h1>

<h2><i class="fas fa-unlink"></i> Orphaned Images
    <span class="badge">${orphanedImages.size()}</span>
</h2>
<p class="muted">Image files on disk that are not referenced by any markdown file.</p>

@if(orphanedImages.isEmpty())
    <p class="muted"><i class="fas fa-check-circle" style="color:#4caf50"></i> No orphaned images found.</p>
@else
    <table class="db-meta-table">
        <thead>
            <tr>
                <th>Image File (relative to docs directory)</th>
            </tr>
        </thead>
        <tbody>
            @for(String img : orphanedImages)
                <tr>
                    <td>${img}</td>
                </tr>
            @endfor
        </tbody>
    </table>
@endif

<hr>

<h2><i class="fas fa-exclamation-triangle"></i> Broken Image Links
    <span class="badge">${brokenLinks.size()}</span>
</h2>
<p class="muted">Image links in markdown files that point to a file that does not exist on disk.</p>

@if(brokenLinks.isEmpty())
    <p class="muted"><i class="fas fa-check-circle" style="color:#4caf50"></i> No broken image links found.</p>
@else
    <table class="db-meta-table">
        <thead>
            <tr>
                <th>Markdown File</th>
                <th>Image Link</th>
            </tr>
        </thead>
        <tbody>
            @for(BrokenImageLink link : brokenLinks)
                <tr>
                    <td>
                        <a href="/markdown?filename=${link.markdownFile()}">${link.markdownFile()}</a>
                    </td>
                    <td class="data-block-currency-error">${link.imageLink()}</td>
                </tr>
            @endfor
        </tbody>
    </table>
@endif

<hr>
<a href="/tools" class="btn-blue-glow"><i class="fas fa-arrow-left"></i>Back to Tools</a>

</body>
</html>

