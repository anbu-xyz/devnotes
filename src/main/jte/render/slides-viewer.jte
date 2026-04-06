@param String slidesHtml
@param String title
@param String markdownFile
@param String lastModifiedTime
@param int slideCount

<!DOCTYPE html>
<html>
<head>
    <title>${title}</title>
</head>
<body>
<div id="slidesContainer">
    $unsafe{slidesHtml}
    <nav class="slide-navigation">
        <button id="prevSlide" class="slide-nav-btn" title="Previous slide (← ↑)">&#8249;</button>
        <span class="slide-counter">1 / ${slideCount}</span>
        <button id="nextSlide" class="slide-nav-btn" title="Next slide (→ ↓ Space)">&#8250;</button>
        <button id="toggleNotes" class="slide-nav-btn" title="Toggle speaker notes (n)">Notes</button>
        <button id="toggleFullscreen" class="slide-nav-btn" title="Fullscreen (f)">⛶</button>
        <a class="slide-nav-btn slide-edit-link"
           href="/markdown?filename=${markdownFile}&edit=true"
           title="Edit in wiki">&#9998;</a>
    </nav>
    <span hidden id="md-file-path">${markdownFile}</span>
    <script>
        (function () {
            var deck = document.querySelector('.slide-deck');
            if (deck && window.SlidesController) {
                window.SlidesController.init(deck, ${slideCount});
            }
            document.querySelectorAll('.mermaid').forEach(function (el) {
                el.dataset.source = el.textContent;
            });
            if (window.mermaid) {
                window.mermaid.run({ querySelector: '.mermaid' });
            }
        })();
    </script>
</div>
</body>
</html>