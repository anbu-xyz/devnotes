@import java.util.Set

@param Set<String> dataSources = new java.util.HashSet<>()

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <script src="https://unpkg.com/htmx.org@1.9.12"></script>
    <script src="https://unpkg.com/htmx.org/dist/ext/json-enc.js"></script>
    <title>Database Metadata Fetcher</title>
</head>
<body class="p-8">
<h1>Database Metadata Fetcher</h1>
<form class="config-form" hx-post="/reExecuteSql" hx-ext="json-enc" hx-target="#result">
    <div class="form-group">
        <label for="datasourceName">Data Source:</label>
        <select id="datasourceName" name="datasourceName" required class="config-input">
            @for(String dataSource : dataSources)
                <option value="${dataSource}">${dataSource}</option>
            @endfor
        </select>
    </div>

    <div class="form-group">
        <label for="sql">SQL Query:</label>
        <textarea id="sql" name="sql" required class="config-input" rows="5"></textarea>
    </div>

    <div class="form-group">
        <label for="markdownFileName">Markdown File Name:</label>
        <input type="text" id="markdownFileName" name="markdownFileName" required class="config-input">
    </div>

    <div class="form-group">
        <label for="codeBlockCounter">Code Block Counter:</label>
        <input type="number" id="codeBlockCounter" name="codeBlockCounter" required class="config-input" value="1">
    </div>

    <div class="form-group">
        <label>Parameter Values:</label>
        <div id="parameterValues">
            <!-- Initial parameter input field -->
            <div class="parameter-input">
            </div>
        </div>
        <button type="button" onclick="addParameterField()" class="btn-blue-glow mt-2">
            <i class="fas fa-plus"></i> Add Parameter
        </button>
    </div>

    <button type="submit" class="btn-blue-glow mt-4">
        <i class="fas fa-play"></i> Execute SQL
    </button>
</form>

<div id="result" class="mt-4"></div>

<a href="/" class="btn-blue-glow mt-4">
    <i class="fas fa-home"></i> Return to Home
</a>

<script>
    let parameterCounter = 0;
    function addParameterField() {
        const container = document.getElementById('parameterValues');
        const newField = document.createElement('div');
        newField.className = 'parameter-input mt-2';
        newField.innerHTML = '<input type="text" name="parameterValues[' + parameterCounter + '].key" placeholder="Parameter Name" class="config-input">' +
                '<input type="text" name="parameterValues[' + parameterCounter + '].value" placeholder="Parameter Value" class="config-input">';
                    container.appendChild(newField);
                    parameterCounter++;
                    }
</script>
</body>
</html>