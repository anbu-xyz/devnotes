@import uk.anbu.devnotes.module.sql.SqlResult
@import java.util.Map

@param SqlResult sqlResult

@for(Map<String, Object> row : sqlResult.getData().rowData())
    <tr>
        @for(SqlResult.Metadata columnMetadata: sqlResult.getData().metadata())
            @if(columnMetadata.javaClass().equals("java.lang.Integer")
               || columnMetadata.javaClass().equals("java.lang.Long")
               || columnMetadata.javaClass().equals("java.lang.Double")
               || columnMetadata.javaClass().equals("java.math.BigDecimal"))
                <td style="text-align: right">${row.get(columnMetadata.name()).toString()}</td>
            @elseif(columnMetadata.javaClass().equals("java.time.OffsetDateTime"))
                <td style="text-align: right">${(row.get(columnMetadata.name())).toString()}</td>
            @else
                <td>${row.get(columnMetadata.name()).toString()}</td>
            @endif
        @endfor
    </tr>
@endfor