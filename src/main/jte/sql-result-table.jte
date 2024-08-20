@import java.util.Map
@import java.util.List
@import gg.jte.support.ForSupport
@import uk.anbu.devnotes.module.SqlResult

@param String outputFileName
@param String datasourceName
@param String markdownFileName
@param String sortColumn
@param String sortDirection
@param SqlResult sqlResult

<div class='sql-result'>
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
        <table class='sortable' data-output-file="${outputFileName}" data-datasource="${datasourceName}"
               data-markdown-file="${markdownFileName}">
            <thead>
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
            <tbody>
            @for(Map<String, Object> row : sqlResult.getData().rowData())
                <tr>
                    @for(SqlResult.Metadata columnMetadata: sqlResult.getData().metadata())
                        <td>${row.get(columnMetadata.name()).toString()}</td>
                    @endfor
                </tr>
            @endfor
            </tbody>
            <tfoot>
                <tr>
                    <td colspan="${sqlResult.getData().metadata().size()}">
                        <button class="btn-blue-glow" onclick='downloadExcel("${outputFileName}")'>
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