@import java.util.Map
@import java.util.List

@param String sqlText
@param String outputFileName
@param String datasourceName
@param String markdownFileName
@param Map<Integer, String> parameterValues
@param List<Map<String, String>> metadata
@param List<Map<String, String>> data

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

    <button class='download-btn' onclick='downloadExcel("${outputFileName}")'>Download Excel</button>
    <button class='re-execute-btn'
            hx-post='/reExecuteSql'
            hx-trigger='click'
            hx-vals='{"sql": "${sqlText.replace("\"", "\\\"")}",
                      "markdownFileName": "${markdownFileName}",
                      "datasourceName": "${datasourceName}"
                      "parameterValues": {
                        @for(Map.Entry<Integer, String> entry : parameterValues.entrySet())
                            "${entry.getKey()}": "${entry.getValue()}",
                        @endfor
                      }'
            hx-target='closest div.sql-result'
            hx-swap='outerHTML'>
        Re-execute SQL
    </button>

    <table class='sortable'>
        <thead>
        <tr>
            @for(Map<String, String> column : metadata)
                <th data-type='${column.get("type")}'>${column.get("name")}</th>
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
    </table>
</div>