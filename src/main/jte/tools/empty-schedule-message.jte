@import java.util.List
@import uk.anbu.devnotes.types.ScriptSchedule
@param List<ScriptSchedule> schedules

@if(schedules.isEmpty())
    <p>No scripts scheduled yet.</p>
@endif