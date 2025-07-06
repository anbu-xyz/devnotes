@import java.util.Map
@import uk.anbu.devnotes.module.sql.SqlResult

@param String outputFileName
@param String datasourceName
@param String markdownFileName
@param String sortColumn
@param String sortDirection
@param SqlResult sqlResult
@param Integer codeBlockCounter
@param Integer maxRowConfig

@if(!sqlResult.isError())
    <div class='sql-result' x-data="{ showSql: false, changedSql: `${sqlResult.getSql().sqlText()}`, originalSql: `${sqlResult.getSql().sqlText()}` }">
        @if(sqlResult.getSql().parameters() != null && !sqlResult.getSql().parameters().keySet().isEmpty())
            <div class='parameters'>
                <h4>Parameters:</h4>
                <ul>
                    @for(Map.Entry<String, Object> entry : sqlResult.getSql().parameters().entrySet())
                        <li>${entry.getKey()}: ${entry.getValue().toString()}</li>
                    @endfor
                </ul>
            </div>
        @endif

        <div class="entry-container">
            <table class='sortable'>
                <thead x-show="!showSql">
                    @template.sql.sql-result-header(sqlResult = sqlResult)
                    @template.sql.sql-result-column-headers(sqlResult = sqlResult,
                        outputFileName = outputFileName, datasourceName = datasourceName,
                        markdownFileName = markdownFileName, sortColumn = sortColumn,
                        sortDirection = sortDirection, codeBlockCounter = codeBlockCounter)
                </thead>
                <tbody x-show="!showSql">
                    @template.sql.sql-result-rows(sqlResult = sqlResult)
                </tbody>
                <tbody x-show="showSql">
                    <tr>
                        <td colspan="${sqlResult.getData().metadata().size()}">
                            <textarea x-model="changedSql" class="w-full h-32 p-2 border rounded"></textarea>
                        </td>
                    </tr>
                </tbody>
                <tfoot>
                    @template.sql.sql-result-footer(sqlResult = sqlResult, outputFileName = outputFileName,
                        datasourceName = datasourceName, markdownFileName = markdownFileName,
                        codeBlockCounter = codeBlockCounter, maxRowConfig = maxRowConfig)
                </tfoot>
            </table>
        </div>
    </div>
@else
    <div class="sql-result" x-data="{ showSql: false, changedSql: `${sqlResult.getSql().sqlText()}`, originalSql: `${sqlResult.getSql().sqlText()}` }">
        <p class="warning">Sql Result Error: check the logs</p>
        <button class="btn-blue-glow" @click="showSql = !showSql">
            <i class="fas fa-code"></i> Edit SQL
        </button>
        <div x-show="showSql">
            <textarea x-model="changedSql" class="w-full h-32 p-2 border rounded"></textarea>
            <button class="btn-blue-glow mt-2"
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
                <i class="fas fa-save"></i> Execute
            </button>
        </div>
    </div>
@endif