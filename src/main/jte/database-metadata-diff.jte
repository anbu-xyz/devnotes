@import java.util.Map
@import java.util.Set

@param String tableName
@param Set<String> matched
@param Map<String, String> undocumented
@param Set<String> ghosts

<div class="db-meta-diff-result">
    <div class="db-meta-diff-title">DB check: ${tableName}</div>
    <div class="db-meta-diff-match">✅ ${matched.size()} column(s) match</div>

    @if(!undocumented.isEmpty())
        <div class="db-meta-diff-undocumented">
            <div>⚠️ Undocumented DB column(s) (${undocumented.size()}):</div>
            <ul>
                @for(Map.Entry<String, String> col : undocumented.entrySet())
                    <li>${col.getKey()}&nbsp;&nbsp;[${col.getValue()}]</li>
                @endfor
            </ul>
        </div>
    @endif

    @if(!ghosts.isEmpty())
        <div class="db-meta-diff-ghost">
            <div>🚫 Documented column(s) not found in DB (${ghosts.size()}):</div>
            <ul>
                @for(String col : ghosts)
                    <li>${col}</li>
                @endfor
            </ul>
        </div>
    @endif
</div>

