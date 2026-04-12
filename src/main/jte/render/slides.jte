@param String markdownFile
@param String title
@param boolean editMode
@param String directoryName
@param java.util.Map<String, Object> urlQueryParams
<!DOCTYPE html>
<html lang="en">
    <head>
        <link rel="stylesheet" href="/css/font-awesome.min.css">
        <link rel="stylesheet" href="/css/prism-tomorrow.min.css">
        <link rel="stylesheet" type="text/css" href="/css/style.css">
        <link rel="stylesheet" type="text/css" href="/css/slides.css">
        <script src="/js/htmx.min.js"></script>
        <title>${title}</title>
    </head>
    <body class="slides-page">
        <div id="slidesContainer">
            Loading...
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
        <script src="/js/prism-bundle.js"></script>
        <script src="/js/mermaid.min.js"></script>
        <script>
            // mermaid is available as a global from mermaid.min.js
            mermaid.initialize({ startOnLoad: false, theme: 'dark' });
            window.mermaid = mermaid;
            // Signal that mermaid is ready so slides-viewer.jte can render diagrams
            // even when the HTMX fragment loads before this module finishes.
            window.dispatchEvent(new CustomEvent('mermaidReady'));
        </script>
        <script src="/js/slides.js"></script>
        <script>
            document.addEventListener('DOMContentLoaded', function () {
                htmx.trigger('#hiddenSlidesButton', 'click');
            });
        </script>
    </body>
</html>