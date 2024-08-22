@import java.util.Map
@import uk.anbu.devnotes.module.SqlResult

@param String outputFileName
@param String datasourceName
@param String markdownFileName
@param String sortColumn
@param String sortDirection
@param SqlResult sqlResult

<div class='sql-result' x-data="{ showSql: false }">
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
        <div>
            <table class='sortable' data-output-file="${outputFileName}" data-datasource="${datasourceName}"
                   data-markdown-file="${markdownFileName}">
                <thead x-show="!showSql">
                    <tr>
                        <td colspan="${sqlResult.getData().metadata().size()}">
                        <span>
                            ${sqlResult.getDatasourceName()}
                        </span>
                        </td>
                    </tr>
                    <tr>
                        @for(SqlResult.Metadata columnMetadata: sqlResult.getData().metadata())
                            <th data-type='${columnMetadata.javaClass()}' data-column='${columnMetadata.name()}' data-output-file="${outputFileName}"
                                data-datasource="${datasourceName}" data-markdown-file="${markdownFileName}"
                                hx-post='/sortTable'
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
                                <pre><code class="language-sql">${sqlResult.getSql().sqlText()}</code></pre>
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
                            <button class="btn-blue-glow" @click="showSql = !showSql">
                                <i class="fas" :class="showSql ? 'fa-table' : 'fa-code'"></i>
                                <!-- Show Table/Show SQL -->
                            </button>
                            <button class="btn-blue-glow" onclick='downloadExcel("${outputFileName}", "${markdownFileName}")'>
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
                                                   "parameterValues":  {}
                                                   }'
                                    hx-target='closest div.sql-result'
                                    hx-swap='outerHTML'>
                                <i class="fas fa-sync"></i> <!-- Re-execute SQL -->
                            </button>
                        </td>
                    </tr>
                </tfoot>
            </table>
        </div>


    </div>
</div>