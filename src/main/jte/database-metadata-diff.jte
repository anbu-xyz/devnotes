@import java.util.Map
@import java.util.Set

@param String tableName
@param Set<String> matched
@param Map<String, String> undocumented
@param Set<String> ghosts
@param String undocumentedYaml

<div class="db-meta-diff-result">
    <div class="db-meta-diff-title">DB check: ${tableName}</div>
    <div class="db-meta-diff-match">✅ ${matched.size()} column(s) match</div>

    @if(!undocumented.isEmpty())
        <div class="db-meta-diff-undocumented">
            <div class="db-meta-diff-undocumented-header">
                <span>⚠️ Undocumented DB column(s) (${undocumented.size()})</span>
                <button class="db-meta-copy-btn"
                        data-yaml="${undocumentedYaml}"
                        onclick="(function(btn){navigator.clipboard.writeText(btn.dataset.yaml).then(function(){var t=btn.textContent;btn.textContent='✅ Copied!';setTimeout(function(){btn.textContent=t;},1500);}).catch(function(){btn.textContent='❌ Failed';setTimeout(function(){btn.textContent=t;},1500);});})(this)">
                    📋 Copy YAML
                </button>
            </div>
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
