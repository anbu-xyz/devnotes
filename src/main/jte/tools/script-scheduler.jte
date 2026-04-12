@import java.util.List
@import java.util.Map
@import uk.anbu.devnotes.types.ScriptSchedule

@param List<ScriptSchedule> schedules
@param Map<String, String> lastRanMap
@param Map<String, String> nextRunMap

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Groovy Script Scheduler</title>
    <link rel="stylesheet" type="text/css" href="/css/font-awesome.min.css">
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" type="text/css" href="/css/script-scheduler.css">
    <script src="/js/htmx.min.js"></script>
</head>
<body>
<div class="container">
    <div class="scheduler-page-header">
        <h1>Groovy Script Scheduler</h1>
        <button type="button" id="open-add-dialog-btn" class="btn-blue-glow">
            <i class="fas fa-plus"></i> Add Schedule
        </button>
    </div>

    <!-- Existing schedules -->
    <div class="existing-schedules">
        <h2>Existing Schedules</h2>
        <div id="empty-message">
            @template.tools.empty-schedule-message(schedules = schedules)
        </div>
        <div id="schedules-list">
            @for(ScriptSchedule schedule : schedules)
                @template.tools.schedule-item(schedule = schedule, lastRan = lastRanMap.getOrDefault(schedule.id(), "Never"), nextRun = nextRunMap.getOrDefault(schedule.id(), "Unknown"))
            @endfor
        </div>
    </div>
</div>

<!-- Add schedule dialog -->
<dialog id="add-schedule-dialog" class="add-schedule-dialog">
    <div class="logs-dialog-header">
        <h3><i class="fas fa-plus"></i> Add New Schedule</h3>
        <button type="button" id="add-dialog-close" class="logs-dialog-close-btn" title="Close">&times;</button>
    </div>
    <div class="add-schedule-dialog-body">
        <form id="add-schedule-form"
              hx-post="/scheduler/add"
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
            <div class="add-schedule-dialog-actions">
                <button type="submit" class="btn-blue-glow">
                    <i class="fas fa-calendar-plus"></i> Schedule Script
                </button>
                <button type="button" id="add-dialog-cancel" class="btn-blue-glow">
                    Cancel
                </button>
            </div>
        </form>
    </div>
</dialog>

<!-- Edit schedule dialog -->
<dialog id="edit-schedule-dialog" class="add-schedule-dialog">
    <div class="logs-dialog-header">
        <h3><i class="fas fa-edit"></i> Edit Schedule</h3>
        <button type="button" id="edit-dialog-close" class="logs-dialog-close-btn" title="Close">&times;</button>
    </div>
    <div class="add-schedule-dialog-body">
        <form id="edit-schedule-form">
            <div class="form-group">
                <label for="editCronExpression">Cron Expression:</label>
                <input type="text" id="editCronExpression" name="cronExpression" required
                       class="form-input"
                       placeholder="*/5 * * * * ?">
                <small>Example: "0 0 12 * * ?" (runs at 12 PM daily)</small>
            </div>
            <div class="form-group">
                <label for="editScriptFile">Script File:</label>
                <input type="text" id="editScriptFile" name="scriptFile" required
                       class="form-input"
                       placeholder="scripts/your-script.groovy">
            </div>
            <div class="add-schedule-dialog-actions">
                <button type="submit" class="btn-blue-glow">
                    <i class="fas fa-save"></i> Save Changes
                </button>
                <button type="button" id="edit-dialog-cancel" class="btn-blue-glow">
                    Cancel
                </button>
            </div>
        </form>
    </div>
</dialog>

<!-- Logs viewer dialog -->
<dialog id="logs-dialog" class="logs-dialog">
    <div class="logs-dialog-header">
        <h3><i class="fas fa-file-alt"></i> Logs: <span id="logs-dialog-script"></span></h3>
        <div class="logs-dialog-header-actions">
            <button type="button" id="logs-clear-btn" class="btn-blue-glow">
                <i class="fas fa-trash"></i> Clear All Logs
            </button>
            <button type="button" id="logs-dialog-close" class="logs-dialog-close-btn" title="Close">&times;</button>
        </div>
    </div>
    <div class="logs-dialog-body">
        <div class="logs-list-panel" id="logs-list-panel">
            <div class="logs-empty">Select a schedule to view logs.</div>
        </div>
        <div class="logs-content-panel" id="logs-content-panel">
            <div class="logs-empty">Select a log entry to view its content.</div>
        </div>
    </div>
</dialog>

<script>
    // ── Edit-schedule dialog ─────────────────────────────────────────────────
    const editDialog     = document.getElementById('edit-schedule-dialog');
    const editDialogForm = document.getElementById('edit-schedule-form');

    function openEditDialog(scheduleId, cronExpression, scriptFile) {
        document.getElementById('editCronExpression').value = cronExpression;
        document.getElementById('editScriptFile').value     = scriptFile;
        // Point HTMX at the specific schedule item so the swap replaces it in-place
        editDialogForm.setAttribute('hx-post',   '/scheduler/edit/' + scheduleId);
        editDialogForm.setAttribute('hx-target', '#schedule-' + scheduleId);
        editDialogForm.setAttribute('hx-swap',   'outerHTML');
        htmx.process(editDialogForm);
        editDialog.showModal();
    }

    function closeEditDialog() { editDialog.close(); }

    document.getElementById('edit-dialog-close').addEventListener('click',  closeEditDialog);
    document.getElementById('edit-dialog-cancel').addEventListener('click', closeEditDialog);
    editDialog.addEventListener('click', function(e) {
        if (e.target === editDialog) closeEditDialog();
    });

    // ── Add-schedule dialog ──────────────────────────────────────────────────
    const addDialog       = document.getElementById('add-schedule-dialog');
    const addDialogForm   = document.getElementById('add-schedule-form');

    document.getElementById('open-add-dialog-btn').addEventListener('click', function() {
        addDialogForm.reset();
        addDialog.showModal();
    });

    function closeAddDialog() { addDialog.close(); }

    document.getElementById('add-dialog-close').addEventListener('click', closeAddDialog);
    document.getElementById('add-dialog-cancel').addEventListener('click', closeAddDialog);
    addDialog.addEventListener('click', function(e) {
        if (e.target === addDialog) closeAddDialog();
    });

    // ── HTMX schedule management ─────────────────────────────────────────────
    document.body.addEventListener('htmx:afterRequest', function(evt) {
        if (evt.detail.successful) {
            if (evt.detail.requestConfig.path === '/scheduler/add') {
                closeAddDialog();
                htmx.ajax('GET', '/scheduler/empty-message', {target: '#empty-message'});
            } else if (evt.detail.requestConfig.path.startsWith('/scheduler/edit/')) {
                closeEditDialog();
            } else if (evt.detail.requestConfig.path.startsWith('/scheduler/delete')) {
                htmx.ajax('GET', '/scheduler/empty-message', {target: '#empty-message'});
            }
        }
    });

    // ── Logs dialog ──────────────────────────────────────────────────────────
    const logsDialog       = document.getElementById('logs-dialog');
    const logsDialogScript = document.getElementById('logs-dialog-script');
    const logsListPanel    = document.getElementById('logs-list-panel');
    const logsContentPanel = document.getElementById('logs-content-panel');

    let currentScheduleId = null;

    function escHtml(str) {
        return str
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function openLogsDialog(scheduleId, scriptFile) {
        currentScheduleId = scheduleId;
        logsDialogScript.textContent = scriptFile;
        logsListPanel.innerHTML    = '<div class="logs-empty">Loading\u2026</div>';
        logsContentPanel.innerHTML = '<div class="logs-empty">Loading\u2026</div>';
        logsDialog.showModal();

        fetch('/scheduler/logs?scheduleId=' + encodeURIComponent(scheduleId))
            .then(function(r) { return r.json(); })
            .then(function(entries) {
                if (!entries || entries.length === 0) {
                    logsListPanel.innerHTML    = '<div class="logs-empty">No logs found.</div>';
                    logsContentPanel.innerHTML = '<div class="logs-empty">No logs found.</div>';
                    return;
                }
                logsListPanel.innerHTML = entries.map(function(e, i) {
                    return '<div class="logs-list-entry' + (i === 0 ? ' active' : '') + '" ' +
                           'data-file="' + escHtml(e.fileName) + '">' +
                           escHtml(e.displayName) + '</div>';
                }).join('');
                // Auto-load the most recent entry
                showLogContent(scheduleId, entries[0].fileName);
            })
            .catch(function(err) {
                logsListPanel.innerHTML = '<div class="logs-empty">Error loading logs.</div>';
                logsContentPanel.innerHTML = '<div class="logs-empty">Error loading logs.</div>';
                console.error(err);
            });
    }

    function showLogContent(scheduleId, fileName) {
        logsContentPanel.innerHTML = '<div class="logs-empty">Loading\u2026</div>';
        fetch('/scheduler/log-content?scheduleId=' + encodeURIComponent(scheduleId) +
              '&logFile=' + encodeURIComponent(fileName))
            .then(function(r) { return r.text(); })
            .then(function(text) {
                logsContentPanel.innerHTML = '<pre>' + escHtml(text) + '</pre>';
            })
            .catch(function(err) {
                logsContentPanel.innerHTML = '<div class="logs-empty">Error loading content.</div>';
                console.error(err);
            });
    }

    // Event delegation – works for items added dynamically via HTMX too
    document.body.addEventListener('click', function(e) {
        // Execute Now
        var execBtn = e.target.closest('.schedule-execute-btn');
        if (execBtn) {
            executeScheduleNow(execBtn.dataset.scheduleId, execBtn.dataset.scriptFile, execBtn);
            return;
        }

        // Open edit dialog
        var editBtn = e.target.closest('.schedule-edit-btn');
        if (editBtn) {
            openEditDialog(editBtn.dataset.scheduleId,
                           editBtn.dataset.cronExpression,
                           editBtn.dataset.scriptFile);
            return;
        }

        // Open logs dialog (last-ran link)
        var logsBtn = e.target.closest('.schedule-logs-btn');
        if (logsBtn) {
            e.preventDefault();
            openLogsDialog(logsBtn.dataset.scheduleId, logsBtn.dataset.scriptFile);
            return;
        }

        // Select log entry inside dialog
        var entry = e.target.closest('.logs-list-entry');
        if (entry && logsListPanel.contains(entry)) {
            logsListPanel.querySelectorAll('.logs-list-entry')
                .forEach(function(el) { el.classList.remove('active'); });
            entry.classList.add('active');
            showLogContent(currentScheduleId, entry.dataset.file);
            return;
        }
    });

    // Close button
    document.getElementById('logs-dialog-close').addEventListener('click', function() {
        logsDialog.close();
    });

    // Click on backdrop closes dialog
    logsDialog.addEventListener('click', function(e) {
        if (e.target === logsDialog) logsDialog.close();
    });

    // Clear all logs
    document.getElementById('logs-clear-btn').addEventListener('click', function() {
        if (!currentScheduleId) return;
        if (!confirm('Delete all log files for this schedule? This cannot be undone.')) return;
        fetch('/scheduler/logs?scheduleId=' + encodeURIComponent(currentScheduleId), {
            method: 'DELETE'
        }).then(function(r) {
            if (r.ok) {
                logsListPanel.innerHTML    = '<div class="logs-empty">No logs found.</div>';
                logsContentPanel.innerHTML = '<div class="logs-empty">No logs found.</div>';
                resetLastRanToNever(currentScheduleId);
            }
        }).catch(function(err) { console.error('Failed to clear logs', err); });
    });

    function resetLastRanToNever(scheduleId) {
        var item = document.getElementById('schedule-' + scheduleId);
        if (!item) return;
        var span = item.querySelector('.schedule-last-ran');
        if (!span) return;
        var icon = span.querySelector('i');
        span.innerHTML = '';
        if (icon) span.appendChild(icon);
        span.insertAdjacentText('beforeend', ' Last ran: ');
        var never = document.createElement('span');
        never.className = 'last-ran-value';
        never.textContent = 'Never';
        span.appendChild(never);
    }

    // ── Execute Now ──────────────────────────────────────────────────────────
    function executeScheduleNow(scheduleId, scriptFile, btn) {
        var savedHtml = btn.innerHTML;
        btn.disabled = true;
        btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Running\u2026';

        fetch('/scheduler/execute/' + encodeURIComponent(scheduleId), { method: 'POST' })
            .then(function(r) { return r.json().then(function(body) { return { ok: r.ok, body: body }; }); })
            .then(function(res) {
                btn.disabled = false;
                btn.innerHTML = savedHtml;
                if (!res.ok) {
                    alert('Execution failed: ' + (res.body.error || 'unknown error'));
                    return;
                }
                updateLastRanLink(scheduleId, scriptFile, res.body.displayName);
                openLogsDialog(scheduleId, scriptFile);
            })
            .catch(function(err) {
                btn.disabled = false;
                btn.innerHTML = savedHtml;
                console.error('Execute failed', err);
                alert('Execution failed: ' + err.message);
            });
    }

    function updateLastRanLink(scheduleId, scriptFile, displayName) {
        var item = document.getElementById('schedule-' + scheduleId);
        if (!item) return;
        var span = item.querySelector('.schedule-last-ran');
        if (!span) return;
        var icon = span.querySelector('i');
        span.innerHTML = '';
        if (icon) span.appendChild(icon);
        span.insertAdjacentText('beforeend', ' Last ran: ');
        var link = document.createElement('a');
        link.href = '#';
        link.className = 'last-ran-link schedule-logs-btn';
        link.dataset.scheduleId = scheduleId;
        link.dataset.scriptFile = scriptFile;
        link.textContent = displayName;
        span.appendChild(link);
    }
</script>

</body>
</html>

