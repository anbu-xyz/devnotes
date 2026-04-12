@import uk.anbu.devnotes.types.FlashCard

@param FlashCard card
@param String questionHtml
@param String answerHtml
@param int remaining
@param int sessionTotal
@param String encodedPath

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="/generated/css/font-awesome.min.css">
    <script defer src="/generated/js/alpine.min.js"></script>
    <title>Flash Cards - Random Review</title>
</head>
<body>
<h1><i class="fas fa-random"></i> Flash Cards - Random Review</h1>

@if(card == null)
    <%-- ── Session complete ──────────────────────────────────────────────── --%>
    <div class="config-section" style="text-align:center; padding:2.5rem 1rem;">
        <div style="font-size:3rem; line-height:1;">🎉</div>
        <h2 style="margin-top:0.75rem; color:#3fb950;">Session complete!</h2>
        @if(sessionTotal > 0)
            <p>You reviewed <strong>${sessionTotal}</strong> card${sessionTotal == 1 ? "" : "s"}.</p>
        @else
            <p>No cards were available for this session.</p>
        @endif
        <div style="margin-top:1.5rem; display:flex; gap:0.75rem; justify-content:center; flex-wrap:wrap;">
            <a href="/flashcards" class="btn-blue-glow">
                <i class="fas fa-arrow-left"></i>Back to Summary
            </a>
            <a href="/flashcards/review" class="btn-blue-glow">
                <i class="fas fa-play"></i>Review All Due
            </a>
        </div>
    </div>

@else
    <%-- ── Review card ─────────────────────────────────────────────────── --%>
    <div x-data="{ answerVisible: false }"
         @keydown.window="
            if (answerVisible) {
                const k = $event.key;
                if (k >= '0' && k <= '5') {
                    $event.preventDefault();
                    document.getElementById('rate-btn-' + k).click();
                }
            }
         ">

        <%-- Progress bar + edit link --%>
        <div class="fc-review-header">
            <span class="fc-progress-label">
                <span style="color:#aaa;">Card</span>
                <strong>${sessionTotal - remaining}</strong>
                <span style="color:#555;">/</span>
                <strong>${sessionTotal}</strong>
                <span style="color:#555;">&nbsp;·&nbsp;</span>
                <span style="color:#aaa;">${remaining} remaining</span>
            </span>
            <div style="display:flex; gap:0.5rem;">
                <a href="/flashcards/edit/${encodedPath}" class="btn-blue-glow">
                    <i class="fas fa-edit"></i>Edit Card
                </a>
                <a href="/flashcards" class="btn-blue-glow" style="border-color:#555; color:#aaa;">
                    <i class="fas fa-times"></i>End Session
                </a>
            </div>
        </div>

        <%-- Progress bar visual --%>
        <div class="fc-progress-bar-track">
            <div class="fc-progress-bar-fill"
                 style="width: ${sessionTotal > 0 ? (sessionTotal - remaining) * 100 / sessionTotal : 0}%"></div>
        </div>

        <%-- Question panel --%>
        <div class="config-section fc-card-panel">
            <h3 class="fc-panel-title fc-question-title">
                <i class="fas fa-question-circle"></i> Question
            </h3>
            <div class="fc-content">
                $unsafe{questionHtml}
            </div>
        </div>

        <%-- Show Answer button --%>
        <div style="text-align:center; margin:1rem 0;">
            <button class="btn-blue-glow fc-show-answer-btn"
                    @click="answerVisible = true"
                    x-show="!answerVisible">
                <i class="fas fa-eye"></i>Show Answer
            </button>
        </div>

        <%-- Answer + rating (hidden until revealed) --%>
        <div x-show="answerVisible" x-cloak>

            <%-- Answer panel --%>
            <div class="config-section fc-card-panel">
                <h3 class="fc-panel-title fc-answer-title">
                    <i class="fas fa-check-circle"></i> Answer
                </h3>
                <div class="fc-content">
                    $unsafe{answerHtml}
                </div>
            </div>

            <%-- Rating panel --%>
            <div class="config-section" style="margin-top:1rem;">
                <h3 style="margin-bottom:0.75rem;">
                    How well did you remember?
                    <span class="fc-key-hint">(or press 0–5)</span>
                </h3>
                <div class="fc-rating-row">

                    <form action="/flashcards/review/random" method="post">
                        <input type="hidden" name="relativePath" value="${card.getRelativePath()}">
                        <input type="hidden" name="quality"      value="0">
                        <button type="submit" id="rate-btn-0" class="btn-blue-glow fc-rate-btn fc-rate-0" title="Press 0">
                            0 - Blackout
                        </button>
                    </form>

                    <form action="/flashcards/review/random" method="post">
                        <input type="hidden" name="relativePath" value="${card.getRelativePath()}">
                        <input type="hidden" name="quality"      value="1">
                        <button type="submit" id="rate-btn-1" class="btn-blue-glow fc-rate-btn fc-rate-1" title="Press 1">
                            1 - Wrong
                        </button>
                    </form>

                    <form action="/flashcards/review/random" method="post">
                        <input type="hidden" name="relativePath" value="${card.getRelativePath()}">
                        <input type="hidden" name="quality"      value="2">
                        <button type="submit" id="rate-btn-2" class="btn-blue-glow fc-rate-btn fc-rate-2" title="Press 2">
                            2 - Forgot
                        </button>
                    </form>

                    <form action="/flashcards/review/random" method="post">
                        <input type="hidden" name="relativePath" value="${card.getRelativePath()}">
                        <input type="hidden" name="quality"      value="3">
                        <button type="submit" id="rate-btn-3" class="btn-blue-glow fc-rate-btn fc-rate-3" title="Press 3">
                            3 - Hard
                        </button>
                    </form>

                    <form action="/flashcards/review/random" method="post">
                        <input type="hidden" name="relativePath" value="${card.getRelativePath()}">
                        <input type="hidden" name="quality"      value="4">
                        <button type="submit" id="rate-btn-4" class="btn-blue-glow fc-rate-btn fc-rate-4" title="Press 4">
                            4 - Good
                        </button>
                    </form>

                    <form action="/flashcards/review/random" method="post">
                        <input type="hidden" name="relativePath" value="${card.getRelativePath()}">
                        <input type="hidden" name="quality"      value="5">
                        <button type="submit" id="rate-btn-5" class="btn-blue-glow fc-rate-btn fc-rate-5" title="Press 5">
                            5 - Easy
                        </button>
                    </form>

                </div>
                <p class="fc-rating-legend">
                    0 Blackout &nbsp;·&nbsp; 1 Wrong &nbsp;·&nbsp; 2 Forgot
                    &nbsp;·&nbsp; 3 Hard &nbsp;·&nbsp; 4 Good &nbsp;·&nbsp; 5 Easy
                </p>
            </div>
        </div><%-- end answerVisible --%>

    </div><%-- end x-data --%>
@endif

<hr style="margin-top:2rem;">
<a href="/flashcards" class="btn-blue-glow">
    <i class="fas fa-arrow-left"></i>Back to Summary
</a>

<style>
.fc-review-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 0.5rem;
}
.fc-progress-label {
    font-size: 0.85rem;
}
.fc-progress-bar-track {
    height: 4px;
    background: #2a2a2a;
    border-radius: 2px;
    margin-bottom: 1rem;
    overflow: hidden;
}
.fc-progress-bar-fill {
    height: 100%;
    background: #29b4fd;
    border-radius: 2px;
    transition: width 0.3s ease;
}
.fc-card-panel {
    margin-bottom: 0;
}
.fc-panel-title {
    margin-bottom: 0.75rem;
    font-size: 0.9rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
}
.fc-question-title { color: #7ec8e3; }
.fc-answer-title   { color: #3fb950; }
.fc-content {
    line-height: 1.75;
}
.fc-content p:first-child { margin-top: 0; }
.fc-content p:last-child  { margin-bottom: 0; }
.fc-content pre, .fc-content code { max-height: none; }
.fc-show-answer-btn {
    font-size: 1rem;
    padding: 0.5rem 2.5rem;
}
.fc-rating-row {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
}
.fc-rate-btn {
    min-width: 7.5rem;
    text-align: center;
}
.fc-rate-0 { border-color: #f85149; color: #f85149; }
.fc-rate-1 { border-color: #d29922; color: #d29922; }
.fc-rate-2 { border-color: #d29922; color: #d29922; }
.fc-rate-3 { border-color: #58a6ff; color: #58a6ff; }
.fc-rate-4 { border-color: #3fb950; color: #3fb950; }
.fc-rate-5 { border-color: #3fb950; color: #3fb950; }
.fc-rate-0:hover { background-color: #f85149; color: #121212; }
.fc-rate-1:hover { background-color: #d29922; color: #121212; }
.fc-rate-2:hover { background-color: #d29922; color: #121212; }
.fc-rate-3:hover { background-color: #58a6ff; color: #121212; }
.fc-rate-4:hover { background-color: #3fb950; color: #121212; }
.fc-rate-5:hover { background-color: #3fb950; color: #121212; }
.fc-key-hint {
    color: #555;
    font-size: 0.75rem;
    font-weight: normal;
}
.fc-rating-legend {
    color: #555;
    font-size: 0.72rem;
    margin-top: 0.5rem;
    margin-bottom: 0;
}
</style>
</body>
</html>

