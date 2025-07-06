@import java.util.List
@import uk.anbu.devnotes.types.ScriptSchedule

@param List<ScriptSchedule> schedules

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Groovy Script Scheduler</title>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" type="text/css" href="/css/script-scheduler.css">
    <script src="https://unpkg.com/htmx.org@1.9.12"></script>
</head>
<body>
<div class="container">
    <h1>Groovy Script Scheduler</h1>

    <!-- Add new schedule form -->
    <div class="schedule-form">
        <h2>Add New Schedule</h2>
        <form hx-post="/scheduler/add"
              hx-target="#schedules-list"
              hx-swap="beforeend">
            <div class="form-group">
                <label for="cronExpression">Cron Expression:</label>
                <input type="text" id="cronExpression" name="cronExpression" required
                       placeholder="*/5 * * * * ?" class="form-input">
                <small>Example: "0 0 12 * * ?" (runs at 12 PM daily)</small>
            </div>

            <div class="form-group">
                <label for="scriptFile">Script File:</label>
                <input type="text" id="scriptFile" name="scriptFile" required
                       class="form-input"
                       placeholder="scripts/your-script.groovy">
            </div>

            <button type="submit" class="btn-primary">
                <span class="button-text">Schedule Script</span>
            </button>
        </form>
    </div>

    <!-- Existing schedules -->
    <div class="existing-schedules">
        <h2>Existing Schedules</h2>
        <div id="empty-message">
            @template.tools.empty-schedule-message(schedules = schedules)
        </div>
        <div id="schedules-list">
            @for(ScriptSchedule schedule : schedules)
                @template.tools.schedule-item(schedule = schedule)
            @endfor
        </div>
    </div>
</div>

<script>
    document.body.addEventListener('htmx:afterRequest', function(evt) {
        if (evt.detail.successful) {
            if (evt.detail.requestConfig.path === '/scheduler/add') {
                evt.detail.requestConfig.elt.reset();
                htmx.ajax('GET', '/scheduler/empty-message', {target: '#empty-message'});
            } else if (evt.detail.requestConfig.path.startsWith('/scheduler/delete')) {
                htmx.ajax('GET', '/scheduler/empty-message', {target: '#empty-message'});
            }
        }
    });
</script>

</body>
</html>