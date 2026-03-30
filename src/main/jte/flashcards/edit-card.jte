@import uk.anbu.devnotes.types.FlashCard
@import java.time.format.DateTimeFormatter

@param FlashCard card
@param String encodedPath
@param String error

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <title>Flash Cards — Edit Card</title>
</head>
<body>
<h1><i class="fas fa-layer-group"></i> Flash Cards — Edit Card</h1>

@if(error != null)
    <div class="encryption-error">
        <i class="fas fa-exclamation-triangle"></i> ${error}
    </div>
@endif

<form action="/flashcards/edit/${encodedPath}" method="post" class="config-form">

    <div class="config-section">

        <div class="form-group">
            <label for="question">
                Question
                <span style="color:#aaa; font-weight:normal;">— CommonMark markdown supported</span>
            </label>
            <textarea id="question"
                      name="question"
                      class="config-input fc-textarea"
                      required>${card.getQuestion()}</textarea>
        </div>

        <div class="form-group">
            <label for="answer">
                Answer
                <span style="color:#aaa; font-weight:normal;">— CommonMark markdown supported</span>
            </label>
            <textarea id="answer"
                      name="answer"
                      class="config-input fc-textarea"
                      required>${card.getAnswer()}</textarea>
        </div>

    </div>

    <%-- Read-only metadata --%>
    <div class="config-section">
        <h3 style="margin-bottom:0.75rem; color:#aaa; font-size:0.85rem; text-transform:uppercase; letter-spacing:0.05em;">
            Card Metadata
        </h3>
        <table class="fc-meta-table">
            <tbody>
                <tr>
                    <th>Topic</th>
                    <td>${card.getTopic().isEmpty() ? "(root)" : card.getTopic()}</td>
                </tr>
                <tr>
                    <th>Reviews</th>
                    <td>${card.getReviewCount()} (${card.getCorrectCount()} correct / ${card.getIncorrectCount()} incorrect)</td>
                </tr>
                <tr>
                    <th>Ease Factor</th>
                    <td>${String.format("%.2f", card.getEaseFactor())}</td>
                </tr>
                <tr>
                    <th>Interval</th>
                    <td>${card.getInterval()} day(s)</td>
                </tr>
                <tr>
                    <th>Last Reviewed</th>
                    <td>
                        @if(card.getLastReviewed() != null)
                            ${card.getLastReviewed().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}
                        @else
                            <span style="color:#555;">Never</span>
                        @endif
                    </td>
                </tr>
                <tr>
                    <th>Next Review</th>
                    <td>
                        @if(card.getNextReview() != null)
                            ${card.getNextReview().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}
                        @else
                            <span style="color:#f0c040;">Due immediately</span>
                        @endif
                    </td>
                </tr>
            </tbody>
        </table>
    </div>

    <div class="form-actions">
        <button type="submit" class="btn-blue-glow">
            <i class="fas fa-save"></i>Save
        </button>
        <a href="/flashcards" class="btn-blue-glow">
            <i class="fas fa-times"></i>Cancel
        </a>
    </div>

</form>

<style>
.fc-textarea {
    min-height: 8rem;
    resize: vertical;
    font-family: 'Courier New', Courier, monospace;
    font-size: 0.85rem;
    line-height: 1.5;
    white-space: pre;
}
.fc-meta-table {
    width: 100%;
    border-collapse: collapse;
}
.fc-meta-table th {
    text-align: left;
    color: #aaa;
    font-weight: normal;
    width: 140px;
    padding: 0.25rem 0.5rem 0.25rem 0;
    background: transparent;
}
.fc-meta-table td {
    color: #e0e0e0;
    padding: 0.25rem 0;
    background: transparent;
    border: none;
}
.fc-meta-table th, .fc-meta-table td {
    border-bottom: 1px solid #2a2a2a;
}
</style>
</body>
</html>

