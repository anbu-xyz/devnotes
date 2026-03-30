@import java.util.List

@param List<String> topics
@param String selectedTopic
@param String error

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <title>Flash Cards — New Card</title>
</head>
<body>
<h1><i class="fas fa-layer-group"></i> Flash Cards — New Card</h1>

@if(error != null)
    <div class="encryption-error">
        <i class="fas fa-exclamation-triangle"></i> ${error}
    </div>
@endif

<form action="/flashcards/new" method="post" class="config-form">

    <div class="config-section">

        <div class="form-group">
            <label for="topic">Topic <span style="color:#aaa; font-weight:normal;">(directory path, e.g. java/streams)</span></label>
            <input type="text"
                   id="topic"
                   name="topic"
                   class="config-input"
                   value="${selectedTopic != null ? selectedTopic : ""}"
                   placeholder="e.g. java/streams"
                   list="topics-list"
                   autocomplete="off">
            <datalist id="topics-list">
                @for(String t : topics)
                    <option value="${t}"></option>
                @endfor
            </datalist>
        </div>

        <div class="form-group">
            <label for="question">
                Question
                <span style="color:#aaa; font-weight:normal;">— CommonMark markdown supported (bold, code fences, lists…)</span>
            </label>
            <textarea id="question"
                      name="question"
                      class="config-input fc-textarea"
                      placeholder="Enter your question here. Markdown is supported — e.g. **bold**, `inline code`, fenced code blocks."
                      required></textarea>
        </div>

        <div class="form-group">
            <label for="answer">
                Answer
                <span style="color:#aaa; font-weight:normal;">— CommonMark markdown supported</span>
            </label>
            <textarea id="answer"
                      name="answer"
                      class="config-input fc-textarea"
                      placeholder="Enter the answer here. Markdown is supported — e.g. bullet lists, **bold**, `code`."
                      required></textarea>
        </div>

    </div>

    <div class="form-actions">
        <button type="submit" class="btn-blue-glow">
            <i class="fas fa-save"></i>Save Card
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
</style>
</body>
</html>

