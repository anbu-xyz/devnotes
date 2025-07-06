@import uk.anbu.devnotes.module.sql.SqlResult

@param SqlResult sqlResult

<tr>
    <td colspan="${sqlResult.getData().metadata().size()}" class="sql-result-header">
        <span class="sql-datasource-name">
            ${sqlResult.getDatasourceName()}
        </span>
        <span class="sql-execution-time">
            ${sqlResult.getExecutionTime().format(java.time.format.DateTimeFormatter.ofPattern("dd/MMM HH:mm"))}
        </span>
    </td>
</tr>