@import uk.anbu.devtools.service.ConfigService.DataSourceConfig
@import java.util.Map

@param String markdownDirectory
@param String sshKeyFile
@param Map<String, DataSourceConfig> dataSources

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <title>Configuration</title>
</head>
<body class="p-8">
<h1>Configuration</h1>
<form action="/config" method="post" class="config-form">
    Markdown Directory: ${markdownDirectory}<br>
    SSH Key File: ${sshKeyFile}

    <h2>Database Configurations</h2>
    @for(var entry : dataSources.entrySet())
        <div class="form-group">
            <h3>${entry.getKey()}</h3>
            <label for="${entry.getKey()}-url">URL:</label>
            <input type="text" id="${entry.getKey()}-url" name="datasources[${entry.getKey()}].url" value="${entry.getValue().url()}" class="config-input">

            <label for="${entry.getKey()}-username">Username:</label>
            <input type="text" id="${entry.getKey()}-username" name="datasources[${entry.getKey()}].username" value="${entry.getValue().username()}" class="config-input">

            <label for="${entry.getKey()}-password">Password:</label>
            <input type="password" id="${entry.getKey()}-password" name="datasources[${entry.getKey()}].password" value="********" class="config-input">

            <label for="${entry.getKey()}-driverClassName">Driver Class Name:</label>
            <input type="text" id="${entry.getKey()}-driverClassName" name="datasources[${entry.getKey()}].driverClassName" value="${entry.getValue().driverClassName()}" class="config-input">
        </div>
    @endfor

    <button type="submit" class="create-button">
        <i class="fas fa-save"></i>Save And Reload
    </button>
</form>
<a href="/" class="create-button mt-4">
    <i class="fas fa-home"></i>Return to Home
</a>
</body>
</html>