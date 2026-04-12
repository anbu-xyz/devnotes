@import uk.anbu.devnotes.types.ScriptSchedule
@param ScriptSchedule schedule
@param String lastRan
@param String nextRun

<div class="schedule-item" id="schedule-${schedule.id()}">
    <div class="schedule-header">
        <span class="cron-expression">
            <i class="fas fa-clock"></i> ${schedule.cronExpression()}
            <small class="next-run-hint">Next: ${nextRun}</small>
        </span>
        <div class="schedule-actions">
            <button type="button" class="btn-blue-glow schedule-execute-btn"
                    data-schedule-id="${schedule.id()}"
                    data-script-file="${schedule.scriptFile()}">
                <i class="fas fa-play"></i> Execute Now
            </button>
            <button type="button" class="btn-blue-glow schedule-edit-btn"
                    data-schedule-id="${schedule.id()}"
                    data-cron-expression="${schedule.cronExpression()}"
                    data-script-file="${schedule.scriptFile()}">
                <i class="fas fa-edit"></i> Edit
            </button>
            <button class="btn-blue-glow"
                    hx-delete="/scheduler/delete/${schedule.id()}"
                    hx-target="#schedule-${schedule.id()}"
                    hx-swap="outerHTML">
                <i class="fas fa-trash"></i> Delete
            </button>
        </div>
    </div>
    <div class="schedule-meta">
        <span class="schedule-script-file"><i class="fas fa-file-code"></i> ${schedule.scriptFile()}</span>
        <span class="schedule-last-ran"><i class="fas fa-history"></i> Last ran:
            @if(!lastRan.equals("Never"))
                <a href="#" class="last-ran-link schedule-logs-btn"
                   data-schedule-id="${schedule.id()}"
                   data-script-file="${schedule.scriptFile()}">${lastRan}</a>
            @else
                <span class="last-ran-value">Never</span>
            @endif
        </span>
    </div>
</div>