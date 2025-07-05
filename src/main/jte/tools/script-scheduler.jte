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
    <script src="https://unpkg.com/alpinejs" defer></script>
</head>
<body>
<div class="container" x-data="{ schedules: [] }">
    <h1>Groovy Script Scheduler</h1>

    <!-- Add new schedule form -->
    <div class="schedule-form">
        <h2>Add New Schedule</h2>
        <form action="/scheduler/add" method="POST">
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
                       placeholder="/path/to/your/script.groovy">
            </div>

            <button type="submit" class="btn-primary">Schedule Script</button>
        </form>
    </div>

    <!-- Existing schedules -->
    <div class="existing-schedules">
        <h2>Existing Schedules</h2>
        @if(schedules.isEmpty())
            <p>No scripts scheduled yet.</p>
        @else
            @for(ScriptSchedule schedule : schedules)
                <div class="schedule-item">
                    <div class="schedule-header">
                        <span class="cron-expression">Cron: ${schedule.cronExpression()}</span>
                        <div class="schedule-actions">
                            <form action="/scheduler/delete/${schedule.id()}" method="POST" style="display: inline;">
                                <button type="submit" class="btn-danger">Delete</button>
                            </form>
                        </div>
                    </div>

                    <pre class="script-content">Script File: ${schedule.scriptFile()}</pre>
                </div>
            @endfor
        @endif
    </div>
</div>


</body>
</html>