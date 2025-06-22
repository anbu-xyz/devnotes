@import uk.anbu.devnotes.types.SearchResult
@import java.util.List

@param List<SearchResult> results
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
<div class="search-results">
    @if(results.isEmpty())
        <p>No results found for "${searchTerm}"</p>
    @else
        <h2>${results.size()} results found for "${searchTerm}"</h2>

        @for(SearchResult result : results)
            <div class="search-result-item">
                <h3 class="file-name">${result.fileName()}</h3>
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
