@import uk.anbu.devnotes.types.SearchResult
@import uk.anbu.devnotes.types.SearchResultList
@import java.util.List

@param SearchResultList results
@param List<String> extensions
@param String searchTerm

<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Search Results for "${searchTerm}"</title>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
</head>

<body>
<h2>Searched file extensions <span>${String.join(", ", extensions)}</span></h2>
<div class="search-results">
    @if(results.results().isEmpty())
        <p>No results found for "${searchTerm}"</p>
    @else
        <h2>${results.results().size()} results found for "${searchTerm}"</h2>

        @for(SearchResult result : results.results())
            <div class="search-result-item">
                <a href="/markdown?filename=${result.fileName()}">
                    <h3 class="file-name">${result.fileName()}</h3>
                </a>
                @if(result.preview() != null && !result.preview().isEmpty())
                    <p class="content-preview">
                        ${result.preview()}
                    </p>
                @endif
            </div>
        @endfor
    @endif
</div>

</body>

</html>
