@import uk.anbu.devnotes.module.sql.SqlResult

@param SqlResult sqlResult
@param String outputFileName
@param String datasourceName
@param String markdownFileName
@param Integer codeBlockCounter
@param Integer maxRowConfig

<tr>
    <td colspan="${sqlResult.getData().metadata().size()}">
        <span>
            @if(sqlResult.getRowCount() > 0)
                ${sqlResult.getRowCount()} rows
                @if (sqlResult.dbHasMoreRowsThanMaxConfig())
                    (truncated)
                @else
                    (no more data)
                @endif
            @endif
        </span>
        <button class="btn-blue-glow" @click="showSql = !showSql" x-show="!showSql">
            <i class="fas" :class="showSql ? 'fa-table' : 'fa-code'"></i>
        </button>
        <button class="btn-blue-glow"
                x-show="showSql"
                hx-post="/saveSqlChanges"
                hx-trigger="click"
                hx-ext="json-enc"
                :hx-vals="JSON.stringify({
                            datasourceName: '${datasourceName}',
                            markdownFileName: '${markdownFileName}',
                            codeBlockCounter: ${codeBlockCounter},
                            oldSql: originalSql,
                            newSql: changedSql,
                            parameterValues: {},
                            maxRows: ${maxRowConfig},
                            forceExecute: true
                      })"
                hx-target="closest div.sql-result"
                hx-swap="outerHTML">
            <i class="fas fa-save"></i>
        </button>
        <button class="btn-blue-glow" onclick='downloadExcel("${outputFileName}", "${markdownFileName}")' x-show="!showSql">
            <i class="fas fa-file-excel"></i>
        </button>
        <button class='btn-blue-glow'
                hx-post='/reExecuteSql'
                hx-trigger='click'
                hx-ext='json-enc'
                hx-vals='{"sql": "${sqlResult.getSql().sqlText().replaceAll("\n", "\\\\n").replaceAll("\"", "\\\\\"").replaceAll("\t", "\\\\t") }",
                               "datasourceName": "${sqlResult.getDatasourceName()}",
                               "outputFileName": "${outputFileName}",
                               "markdownFileName": "${markdownFileName}",
                               "maxRows": ${maxRowConfig},
                               "parameterValues":  {},
                               "codeBlockCounter": ${codeBlockCounter},
                               "forceExecute": true
                               }'
                hx-target='closest div.sql-result'
                hx-swap='outerHTML'
                x-show="!showSql">
            <i class="fas fa-sync"></i>
        </button>
    </td>
</tr>