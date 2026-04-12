@import uk.anbu.devnotes.types.FlashCard

@param FlashCard card
@param String questionHtml
@param String answerHtml
@param String topic
@param int remaining
@param String nextDueAt
@param String encodedPath

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="/generated/css/font-awesome.min.css">
    <script defer src="/generated/js/alpine.min.js"></script>
    <title>Flash Cards - Review</title>
</head>
<body>
<h1><i class="fas fa-layer-group"></i> Flash Cards - Review</h1>

@if(card == null)
    <%-- ── All caught up ──────────────────────────────────────────────── --%>
    <div class="config-section" style="text-align:center; padding:2.5rem 1rem;">
        <div style="font-size:3rem; line-height:1;">🎉</div>
        <h2 style="margin-top:0.75rem; color:#3fb950;">All caught up!</h2>
        <p>No cards are due right now.</p>
        @if(nextDueAt != null)
            <p style="color:#aaa; font-size:0.85rem;">
                Next card due at: <strong style="color:#e0e0e0;">${nextDueAt}</strong>
            </p>
        @endif
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

        <%-- Progress + edit link --%>
        <div class="fc-review-header">
            <span class="fc-progress-label">
                @if(topic != null && !topic.isEmpty())
                    <span style="color:#aaa;">Topic:</span> <strong>${topic}</strong>
                    <span style="color:#555;">&nbsp;·&nbsp;</span>
                @endif
                <span style="color:#aaa;">${remaining + 1} card(s) remaining</span>
            </span>
            <a href="/flashcards/edit/${encodedPath}" class="btn-blue-glow">
                <i class="fas fa-edit"></i>Edit Card
            </a>
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

                    <form action="/flashcards/review" method="post">
                        <input type="hidden" name="relativePath" value="${card.getRelativePath()}">
                        <input type="hidden" name="quality"      value="0">
                        <input type="hidden" name="topic"        value="${topic}">
                        <button type="submit" id="rate-btn-0" class="btn-blue-glow fc-rate-btn fc-rate-0" title="Press 0">
                            0 - Blackout
                        </button>
                    </form>

                    <form action="/flashcards/review" method="post">
                        <input type="hidden" name="relativePath" value="${card.getRelativePath()}">
                        <input type="hidden" name="quality"      value="1">
                        <input type="hidden" name="topic"        value="${topic}">
                        <button type="submit" id="rate-btn-1" class="btn-blue-glow fc-rate-btn fc-rate-1" title="Press 1">
                            1 - Wrong
                        </button>
                    </form>

                    <form action="/flashcards/review" method="post">
                        <input type="hidden" name="relativePath" value="${card.getRelativePath()}">
                        <input type="hidden" name="quality"      value="2">
                        <input type="hidden" name="topic"        value="${topic}">
                        <button type="submit" id="rate-btn-2" class="btn-blue-glow fc-rate-btn fc-rate-2" title="Press 2">
                            2 - Forgot
                        </button>
                    </form>

                    <form action="/flashcards/review" method="post">
                        <input type="hidden" name="relativePath" value="${card.getRelativePath()}">
                        <input type="hidden" name="quality"      value="3">
                        <input type="hidden" name="topic"        value="${topic}">
                        <button type="submit" id="rate-btn-3" class="btn-blue-glow fc-rate-btn fc-rate-3" title="Press 3">
                            3 - Hard
                        </button>
                    </form>

                    <form action="/flashcards/review" method="post">
                        <input type="hidden" name="relativePath" value="${card.getRelativePath()}">
                        <input type="hidden" name="quality"      value="4">
                        <input type="hidden" name="topic"        value="${topic}">
                        <button type="submit" id="rate-btn-4" class="btn-blue-glow fc-rate-btn fc-rate-4" title="Press 4">
                            4 - Good
                        </button>
                    </form>

                    <form action="/flashcards/review" method="post">
                        <input type="hidden" name="relativePath" value="${card.getRelativePath()}">
                        <input type="hidden" name="quality"      value="5">
                        <input type="hidden" name="topic"        value="${topic}">
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
    margin-bottom: 1rem;
}
.fc-progress-label {
    font-size: 0.85rem;
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

