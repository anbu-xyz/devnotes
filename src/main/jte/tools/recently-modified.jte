@import uk.anbu.devnotes.types.RecentlyModifiedList
@import uk.anbu.devnotes.types.RecentlyModifiedListEntry
@import java.util.List

@param RecentlyModifiedList results
@param List<String> extensions
@param String searchPath

<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" type="text/css" href="/css/style.css">
</head>

<body>
<h2>Searched file extensions <span>${String.join(", ", extensions)}</span>
@if (searchPath != null && !searchPath.isEmpty())
    in directory <span>${searchPath}</span>
@endif
</h2>
<div class="search-results">
    @if(results.results().isEmpty())
        <p>No results found</p>
    @else
        <p>${results.results().size()} results found.</p>

        @for(RecentlyModifiedListEntry result : results.results())
            <div class="search-result-item">
                <a href="/markdown?filename=${result.fileName()}">
                    ${result.fileName()}
                </a>
                @if(result.howLongAgo() != null && !result.howLongAgo().isEmpty())
                    <span>
                        ${result.howLongAgo()}
                    </span>
                @endif
            </div>
        @endfor
    @endif
</div>

</body>

</html>
