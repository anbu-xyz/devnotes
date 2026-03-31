@import uk.anbu.devnotes.types.FlashCardStats
@import java.util.List

@param List<FlashCardStats> topicStats
@param int totalCards
@param int totalDue
@param int reviewedToday
@param double globalAccuracy

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <title>Flash Cards</title>
</head>
<body>
<h1><i class="fas fa-layer-group"></i> Flash Cards</h1>

<%-- Global stats bar --%>
<div class="config-section fc-stats-bar">
    <div class="fc-stat">
        <span class="fc-stat-value">${totalCards}</span>
        <span class="fc-stat-label">Total Cards</span>
    </div>
    <div class="fc-stat">
        <span class="fc-stat-value ${totalDue > 0 ? "fc-value-due" : ""}">${totalDue}</span>
        <span class="fc-stat-label">Due Now</span>
    </div>
    <div class="fc-stat">
        <span class="fc-stat-value">${reviewedToday}</span>
        <span class="fc-stat-label">Reviewed Today</span>
    </div>
    <div class="fc-stat">
        <span class="fc-stat-value">${String.format("%.1f", globalAccuracy)}%</span>
        <span class="fc-stat-label">Overall Accuracy</span>
    </div>
</div>

<%-- Action buttons --%>
<div class="form-actions" style="margin-bottom:1.5rem;">
    <a href="/flashcards/review" class="btn-blue-glow">
        <i class="fas fa-play"></i>Review All Due
    </a>
    <a href="/flashcards/new" class="btn-blue-glow">
        <i class="fas fa-plus"></i>Add New Card
    </a>
    <form method="get" action="/flashcards/review/random" class="fc-random-form">
        <input type="number" name="n" value="10" min="1" max="9999"
               class="fc-random-input" title="Number of cards to review">
        <button type="submit" class="btn-blue-glow">
            <i class="fas fa-random"></i>Random Review
        </button>
    </form>
</div>

<%-- Topic tree table --%>
@if(topicStats.isEmpty())
    <p style="color:#aaa;">No flash cards yet. <a href="/flashcards/new">Create your first card</a>.</p>
@else
    <table style="width:100%;">
        <thead>
            <tr>
                <th style="text-align:left;">Topic</th>
                <th style="text-align:right;">Total</th>
                <th style="text-align:right;">Due</th>
                <th style="text-align:right;">Reviewed Today</th>
                <th style="text-align:right;">Accuracy</th>
                <th style="text-align:right;">Streak</th>
                <th style="text-align:left; padding-left:0.5rem;">Actions</th>
            </tr>
        </thead>
        <tbody>
            @for(FlashCardStats stats : topicStats)
                <tr>
                    <td>${stats.topic().isEmpty() ? "(root)" : stats.topic()}</td>
                    <td style="text-align:right;">${stats.total()}</td>
                    <td style="text-align:right; ${stats.dueNow() > 0 ? "color:#f0c040; font-weight:bold;" : ""}">${stats.dueNow()}</td>
                    <td style="text-align:right;">${stats.reviewedToday()}</td>
                    <td style="text-align:right;">${String.format("%.1f", stats.accuracyPercent())}%</td>
                    <td style="text-align:right;">${stats.currentStreak()}</td>
                    <td style="padding-left:0.5rem;">
                        <a href="/flashcards/review?topic=${stats.topic()}" class="btn-blue-glow">
                            <i class="fas fa-play"></i>Review
                        </a>
                        <a href="/flashcards/new?topic=${stats.topic()}" class="btn-blue-glow">
                            <i class="fas fa-plus"></i>Add
                        </a>
                        <form method="get" action="/flashcards/review/random" class="fc-random-form" style="display:inline-flex;">
                            <input type="hidden" name="topic" value="${stats.topic()}">
                            <input type="number" name="n" value="5" min="1" max="9999"
                                   class="fc-random-input" title="Number of cards to review">
                            <button type="submit" class="btn-blue-glow" title="Random review for this topic">
                                <i class="fas fa-random"></i>
                            </button>
                        </form>
                    </td>
                </tr>
            @endfor
        </tbody>
    </table>
@endif

<hr style="margin-top:2rem;">
<a href="/" class="btn-blue-glow"><i class="fas fa-home"></i>Home</a>

<style>
.fc-stats-bar {
    display: flex;
    gap: 2.5rem;
    flex-wrap: wrap;
    margin-bottom: 1.5rem;
}
.fc-stat {
    display: flex;
    flex-direction: column;
    align-items: center;
    min-width: 90px;
}
.fc-stat-value {
    font-size: 1.8rem;
    font-weight: bold;
    color: #29b4fd;
    line-height: 1.2;
}
.fc-value-due {
    color: #f0c040;
}
.fc-stat-label {
    font-size: 0.72rem;
    color: #aaa;
    margin-top: 0.2rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
}
.fc-random-form {
    display: inline-flex;
    align-items: center;
    gap: 0.25rem;
    margin: 0;
}
.fc-random-input {
    width: 4rem;
    padding: 0.3rem 0.4rem;
    border: 1px solid #444;
    border-radius: 4px;
    background: #1a1a1a;
    color: #e0e0e0;
    font-size: 0.85rem;
    text-align: center;
}
</style>
</body>
</html>

