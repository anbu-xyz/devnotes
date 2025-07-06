@import uk.anbu.devnotes.module.sql.SqlResult

@param SqlResult sqlResult
@param String outputFileName
@param String datasourceName
@param String markdownFileName
@param String sortColumn
@param String sortDirection
@param Integer codeBlockCounter

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
                      "codeBlockCounter": ${codeBlockCounter},
                      "sortDirection": "${columnMetadata.name().equals(sortColumn) ? (sortDirection.equals("asc") ? "desc" : "asc") : "asc"}"
                     }'>
            ${columnMetadata.name()}
            @if(columnMetadata.name().equals(sortColumn))
                <span class="sort-arrow">${sortDirection.equals("asc") ? "▲" : "▼"}</span>
            @endif
        </th>
    @endfor
</tr>