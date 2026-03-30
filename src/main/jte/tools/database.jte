@import java.util.Set

@param Set<String> dataSources

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <title>Database Metadata Fetcher</title>
</head>
<body class="p-8">
<h1>Database Metadata Fetcher</h1>
<form action="/database/fetch-metadata" method="post" class="config-form">
    <div class="form-group">
        <label for="configName">Data Source:</label>
        <select id="configName" name="configName" class="config-input">
            @for(String dataSource : dataSources)
                <option value="${dataSource}">${dataSource}</option>
            @endfor
        </select>
    </div>
    <div class="form-group">
        <label for="targetName">Output File Name:</label>
        <input type="text" id="targetName" name="targetName" class="config-input"
               placeholder="e.g. mydb  →  database/mydb.md" required>
    </div>
    <div class="form-group">
        <label for="schemaPattern">Schema Pattern <span class="text-muted">(optional)</span>:</label>
        <input type="text" id="schemaPattern" name="schemaPattern" class="config-input" placeholder="%">
    </div>
    <div class="form-group">
        <label for="tablePattern">Table Pattern <span class="text-muted">(optional)</span>:</label>
        <input type="text" id="tablePattern" name="tablePattern" class="config-input" placeholder="%">
    </div>
    <button type="submit" class="btn-blue-glow">
        <i class="fas fa-database"></i>Fetch Metadata
    </button>
</form>
<a href="/" class="btn-blue-glow mt-4">
    <i class="fas fa-home"></i>Return to Home
</a>
</body>
</html>