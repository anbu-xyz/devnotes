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
            function runMermaid() {
                var mermaidEls = document.querySelectorAll('.mermaid');
                if (mermaidEls.length === 0) return;
                // Use mermaid.render() instead of mermaid.run() so that diagrams inside
                // hidden slides (display:none → zero dimensions) render without producing
                // the "translate(undefined, NaN)" SVG error that run() causes when it reads
                // offsetWidth/offsetHeight of invisible elements.
                mermaidEls.forEach(function (el, i) {
                    var source = (el.dataset.source || el.textContent).trim();
                    el.dataset.source = source;
                    window.mermaid.render('mermaid-slide-' + i, source)
                        .then(function (result) {
                            el.innerHTML = result.svg;
                        })
                        .catch(function (err) {
                            console.warn('Mermaid render error on slide ' + i + ':', err);
                        });
                });
            }
            if (window.mermaid) {
                runMermaid();
            } else {
                // ESM module is still loading from CDN — wait for the ready signal
                window.addEventListener('mermaidReady', runMermaid, { once: true });
            }
        })();
    </script>
</div>
</body>
</html>