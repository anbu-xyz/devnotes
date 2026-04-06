@param String markdownFile
@param String title
@param boolean editMode
@param String directoryName
@param java.util.Map<String, Object> urlQueryParams
<!DOCTYPE html>
<html lang="en">
    <head>
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/themes/prism-tomorrow.min.css">
        <link rel="stylesheet" type="text/css" href="/css/style.css">
        <link rel="stylesheet" type="text/css" href="/css/slides.css">
        <script src="https://unpkg.com/htmx.org@1.9.12"></script>
        <title>${title}</title>
    </head>
    <body class="slides-page">
        <div id="slidesContainer">
            Loading…
        </div>
        <button hidden id="hiddenSlidesButton"
                hx-trigger="click"
                hx-target="#slidesContainer"
                hx-swap="outerHTML"
                hx-select="div#slidesContainer"
                hx-get="/markdownViewer?filename=${
                    java.net.URLEncoder.encode(markdownFile, java.nio.charset.StandardCharsets.UTF_8)
                }${
                    urlQueryParams == null || urlQueryParams.isEmpty() ? "" : '&' +
                    urlQueryParams.entrySet().stream()
                        .map(e -> java.net.URLEncoder.encode(e.getKey(), java.nio.charset.StandardCharsets.UTF_8)
                                + '=' + java.net.URLEncoder.encode(String.valueOf(e.getValue()), java.nio.charset.StandardCharsets.UTF_8))
                        .collect(java.util.stream.Collectors.joining("&"))
                }">
        </button>
        <div hidden id="dl-current-directory-name">${directoryName}</div>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/prism.min.js"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-java.min.js"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-python.min.js"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-sql.min.js"></script>
        <script type="module">
            import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs';
            mermaid.initialize({ startOnLoad: false, theme: 'dark' });
            window.mermaid = mermaid;
        </script>
        <script src="/js/slides.js"></script>
        <script>
            document.addEventListener('DOMContentLoaded', function () {
                htmx.trigger('#hiddenSlidesButton', 'click');
            });
        </script>
    </body>
</html>