@import java.util.Map
@import uk.anbu.devnotes.module.SqlResult

@param String outputFileName
@param String datasourceName
@param String markdownFileName
@param String sortColumn
@param String sortDirection
@param SqlResult sqlResult
@param Integer codeBlockCounter

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
                    <tr>
                        <td colspan="${sqlResult.getData().metadata().size()}">
                        <span>
                            ${sqlResult.getDatasourceName()}
                        </span>
                        <span style="float: right;">
                            ${sqlResult.getExecutionTime().format(java.time.format.DateTimeFormatter.ofPattern("dd/MMM HH:mm"))}
                        </span>
                        </td>
                    </tr>
                    <tr>
                        @for(SqlResult.Metadata columnMetadata: sqlResult.getData().metadata())
                            <th hx-post='/sortTable'
                                hx-trigger='click'
                                hx-target='closest div.sql-result'
                                hx-ext='json-enc'
                                hx-vals='{"columnName": "${columnMetadata.name()}",
                                      "columnType": "${columnMetadata.javaClass()}",
                                      "outputFileName": "${outputFileName}",
                                      "datasourceName": "${datasourceName}",
                                      "markdownFileName": "${markdownFileName}",
                                      "sortDirection": "${columnMetadata.name().equals(sortColumn) ? (sortDirection.equals("asc") ? "desc" : "asc") : "asc"}"
                                     }'
                            >
                                ${columnMetadata.name()}
                                @if(columnMetadata.name().equals(sortColumn))
                                    <span class="sort-arrow">${sortDirection.equals("asc") ? "▲" : "▼"}</span>
                                @endif
                            </th>
                        @endfor
                    </tr>
                    </thead>
                    <tbody x-show="!showSql">
                    @for(Map<String, Object> row : sqlResult.getData().rowData())
                        <tr>
                            @for(SqlResult.Metadata columnMetadata: sqlResult.getData().metadata())
                                @if(columnMetadata.javaClass().equals("java.lang.Integer")
                                   || columnMetadata.javaClass().equals("java.lang.Long")
                                   || columnMetadata.javaClass().equals("java.lang.Double")
                                   || columnMetadata.javaClass().equals("java.math.BigDecimal"))
                                    <td style="text-align: right">${row.get(columnMetadata.name()).toString()}</td>
                                @else
                                    <td>${row.get(columnMetadata.name()).toString()}</td>
                                @endif
                            @endfor
                        </tr>
                    @endfor
                    </tbody>

                    <tbody x-show="showSql">
                    <tr>
                        <td colspan="${sqlResult.getData().metadata().size()}">
                            <textarea x-model="changedSql" class="w-full h-32 p-2 border rounded"></textarea>
                        </td>
                    </tr>
                    </tbody>
                    <tfoot>
                    <tr>
                        <td colspan="${sqlResult.getData().metadata().size()}">
                            <span>
                                @if(sqlResult.getRowCount() > 0)
                                    ${sqlResult.getRowCount()} rows
                                    @if (sqlResult.hasReachedMaxRows())
                                        (truncated)
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
                                    :hx-vals="encodeSaveSqlChangesRequest({
                                                datasourceName: '${datasourceName}',
                                                markdownFileName: '${markdownFileName}',
                                                codeBlockCounter: ${codeBlockCounter},
                                                oldSql: originalSql,
                                                newSql: changedSql,
                                                parameterValues: {},
                                                forceExecute: true
                                          })"
                                    hx-target="closest div.sql-result"
                                    hx-swap="outerHTML"
                                    >
                                <i class="fas fa-save"></i>
                            </button>
                            <button class="btn-blue-glow" onclick='downloadExcel("${outputFileName}", "${markdownFileName}")' x-show="!showSql">
                                <i class="fas fa-file-excel"></i> <!-- Download Excel -->
                            </button>
                            <button class='btn-blue-glow'
                                    hx-post='/reExecuteSql'
                                    hx-trigger='click'
                                    hx-ext='json-enc'
                                    hx-vals='{"sql": "${sqlResult.getSql().sqlText().replaceAll("\n", "\\\\n").replaceAll("\"", "\\\\\"") }",
                                                   "datasourceName": "${sqlResult.getDatasourceName()}",
                                                   "outputFileName": "${outputFileName}",
                                                   "markdownFileName": "${markdownFileName}",
                                                   "parameterValues":  {},
                                                   "forceExecute": true
                                                   }'
                                    hx-target='closest div.sql-result'
                                    hx-swap='outerHTML'
                                    x-show="!showSql">
                                <i class="fas fa-sync"></i> <!-- Re-execute SQL -->
                            </button>
                        </td>
                    </tr>
                </tfoot>
        </table>
    </div>
</div>

<script>
    function encodeSaveSqlChangesRequest(request) {
        console.log(request);
        return JSON.stringify(request);
    }
</script>