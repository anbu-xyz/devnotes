@import uk.anbu.devnotes.types.SearchResult
@import uk.anbu.devnotes.types.SearchResultList
@import java.util.List

@param SearchResultList results
@param List<String> extensions
@param String searchTerm
@param String searchPath

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Search: ${searchTerm}</title>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" type="text/css" href="/css/search-results.css">
    <link rel="stylesheet" href="/css/font-awesome.min.css">
</head>
<body class="p-8">

<h1><i class="fas fa-search"></i> Search Results</h1>

<div class="mt-4">
    <button class="btn-blue-glow" onclick="history.back()">
        <i class="fas fa-arrow-left"></i> Back
    </button>
</div>

<div class="search-meta">
    <span class="search-meta-query"><i class="fas fa-quote-right"></i> ${searchTerm}</span>
    @if(searchPath != null && !searchPath.isEmpty())
        <span class="search-meta-detail"><i class="fas fa-folder"></i> ${searchPath}</span>
    @endif
    <span class="search-meta-detail"><i class="fas fa-file-alt"></i> ${String.join(", ", extensions)}</span>
</div>

@if(results.results().isEmpty())
    <div class="search-no-results">
        <i class="fas fa-search"></i>
        <p>No results found for "<strong>${searchTerm}</strong>"</p>
    </div>
@else
    <p class="search-count">
        ${results.results().size()} result${results.results().size() == 1 ? "" : "s"} found
    </p>
    <ul class="directory-list">
        @for(SearchResult result : results.results())
            <li class="search-result-item">
                <a href="/markdown?filename=${result.fileName()}" class="search-result-link">
                    <i class="fas fa-file-alt"></i>${result.fileName()}
                </a>
                @if(result.preview() != null && !result.preview().isEmpty())
                    <div class="search-result-preview">${result.preview()}</div>
                @endif
            </li>
        @endfor
    </ul>
@endif

</body>
</html>
