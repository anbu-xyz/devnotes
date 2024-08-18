@import java.util.Map
@import java.util.List
@import gg.jte.support.ForSupport

@param String sqlText
@param String outputFileName
@param String datasourceName
@param String markdownFileName
@param Map<Integer, String> parameterValues
@param List<Map<String, String>> metadata
@param List<Map<String, String>> data
@param String sortColumn
@param String sortDirection

<div class='sql-result'>
    @if(!parameterValues.isEmpty())
        <div class='parameters'>
            <h4>Parameters:</h4>
            <ul>
                @for(Map.Entry<Integer, String> entry : parameterValues.entrySet())
                    <li>${entry.getKey()}: ${entry.getValue()}</li>
                @endfor
            </ul>
        </div>
    @endif

    <div class="entry-container">
        <table class='sortable' data-output-file="${outputFileName}" data-datasource="${datasourceName}"
               data-markdown-file="${markdownFileName}">
            <thead>
            <tr>
                @for(Map<String, String> column : metadata)
                    <th data-type='${column.get("type")}' data-column='${column.get("name")}' data-output-file="${outputFileName}"
                        data-datasource="${datasourceName}" data-markdown-file="${markdownFileName}"
                        hx-post='/sortTable'
                        hx-trigger='click'
                        hx-target='closest div.sql-result'
                        hx-ext='json-enc'
                        hx-vals='{"columnName": "${column.get("name")}",
                                  "columnType": "${column.get("type")}",
                                  "outputFileName": "${outputFileName}",
                                  "datasourceName": "${datasourceName}",
                                  "markdownFileName": "${markdownFileName}",
                                  "sortDirection": "${column.get("name").equals(sortColumn) ? (sortDirection.equals("asc") ? "desc" : "asc") : "asc"}"
                                 }'
                    >
                        ${column.get("name")}
                        @if(column.get("name").equals(sortColumn))
                            <span class="sort-arrow">${sortDirection.equals("asc") ? "▲" : "▼"}</span>
                        @endif
                    </th>
                @endfor
            </tr>
            </thead>
            <tbody>
            @for(Map<String, String> row : data)
                <tr>
                    @for(Map<String, String> column : metadata)
                        <td>${row.get(column.get("name"))}</td>
                    @endfor
                </tr>
            @endfor
            </tbody>
            <tfoot>
                <tr>
                    <td colspan="${metadata.size()}">
                        <button class="btn-blue-glow" onclick='downloadExcel("${outputFileName}")'>
                            <i class="fas fa-file-excel"></i>Download Excel
                        </button>
                        <button class='btn-blue-glow'
                                hx-post='/reExecuteSql'
                                hx-trigger='click'
                                hx-ext='json-enc'
                                hx-vals='{"sql": "${sqlText.replaceAll("\n", "\\\\n").replaceAll("\"", "\\\\\"") }",
                                               "datasourceName": "${datasourceName}",
                                               "outputFileName": "${outputFileName}",
                                               "markdownFileName": "${markdownFileName}",
                                               "parameterValues":  {
                                                    @for(var entry : ForSupport.of(parameterValues.entrySet()))
                                                            @if(entry.getIndex() == 0)
                                                             "${entry.get().getKey()}": "${entry.get().getValue()}"
                                                         @else
                                                             ", ${entry.get().getKey()}": "${entry.get().getValue()}"
                                                         @endif
                                                        @endfor
                                                    }
                                               }'
                                hx-target='closest div.sql-result'
                                hx-swap='outerHTML'>
                            <i class="fas fa-sync"></i>Re-execute SQL
                        </button>
                    </td>
                </tr>
            </tfoot>
        </table>
    </div>
</div>