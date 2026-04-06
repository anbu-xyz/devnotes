/**
 * SlidesController — keyboard-driven slide navigation for devnotes presentation mode.
 *
 * Keyboard shortcuts:
 *   → ↓ Space  next slide
 *   ← ↑        previous slide
 *   n          toggle speaker notes
 *   f          toggle fullscreen
 *   Escape     exit fullscreen
 */
window.SlidesController = (function () {
    let slides = [];
    let current = 0;
    let total = 0;
    let deckEl = null;

    function init(deck, slideCount) {
        deckEl = deck;
        total = slideCount;
        slides = Array.from(deck.querySelectorAll('.slide'));

        if (slides.length === 0) return;

        goto(0);

        document.addEventListener('keydown', onKeyDown);

        const prevBtn = document.getElementById('prevSlide');
        const nextBtn = document.getElementById('nextSlide');
        const notesBtn = document.getElementById('toggleNotes');
        const fsBtn = document.getElementById('toggleFullscreen');

        if (prevBtn) prevBtn.addEventListener('click', prev);
        if (nextBtn) nextBtn.addEventListener('click', next);
        if (notesBtn) notesBtn.addEventListener('click', toggleNotes);
        if (fsBtn) fsBtn.addEventListener('click', toggleFullscreen);
    }

    function goto(index) {
        if (index < 0 || index >= slides.length) return;
        slides.forEach(function (s) { s.classList.remove('active'); });
        slides[index].classList.add('active');
        current = index;
        updateCounter();
        updateNotesButton();
        updateNavButtons();
    }

    function updateNavButtons() {
        var prevBtn = document.getElementById('prevSlide');
        var nextBtn = document.getElementById('nextSlide');
        if (prevBtn) prevBtn.style.visibility = current > 0 ? 'visible' : 'hidden';
        if (nextBtn) nextBtn.style.visibility = current < slides.length - 1 ? 'visible' : 'hidden';
    }

    function updateNotesButton() {
        var btn = document.getElementById('toggleNotes');
        if (!btn) return;
        var notes = slides[current] && slides[current].querySelector('.slide-notes');
        var hasNotes = notes && notes.textContent.trim().length > 0;
        btn.classList.toggle('has-notes', !!hasNotes);
    }

    function next() { goto(current + 1); }
    function prev() { goto(current - 1); }

    function updateCounter() {
        var counter = document.querySelector('.slide-counter');
        if (counter) counter.textContent = (current + 1) + ' / ' + total;
    }

    function toggleNotes() {
        var allNotes = document.querySelectorAll('.slide-notes');
        allNotes.forEach(function (n) { n.classList.toggle('visible'); });
    }

    function toggleFullscreen() {
        if (!document.fullscreenElement) {
            document.documentElement.requestFullscreen().catch(function (e) {
                console.warn('Fullscreen request failed:', e);
            });
        } else {
            document.exitFullscreen();
        }
    }

    function onKeyDown(e) {
        // Don't capture when focus is in an input/textarea
        if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

        switch (e.key) {
            case 'ArrowRight':
            case 'ArrowDown':
            case ' ':
                e.preventDefault();
                next();
                break;
            case 'ArrowLeft':
            case 'ArrowUp':
                e.preventDefault();
                prev();
                break;
            case 'n':
                toggleNotes();
                break;
            case 'f':
                toggleFullscreen();
                break;
            case 'Escape':
                if (document.fullscreenElement) document.exitFullscreen();
                break;
        }
    }

    return { init: init, goto: goto, next: next, prev: prev, toggleNotes: toggleNotes, toggleFullscreen: toggleFullscreen };
})();