@import uk.anbu.devnotes.types.ScriptSchedule
@param ScriptSchedule schedule

<div class="schedule-item" id="schedule-${schedule.id()}">
    <div class="schedule-header">
        <span class="cron-expression">Cron: ${schedule.cronExpression()}</span>
        <div class="schedule-actions">
            <button class="btn-danger"
                    hx-delete="/scheduler/delete/${schedule.id()}"
                    hx-target="#schedule-${schedule.id()}"
                    hx-swap="outerHTML">
                Delete
            </button>
        </div>
    </div>
    <pre class="script-content">Script File: ${schedule.scriptFile()}</pre>
</div>