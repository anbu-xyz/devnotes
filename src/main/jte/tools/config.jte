@import uk.anbu.devnotes.service.ConfigService.DataSourceConfig
@import java.util.Map

@param String markdownDirectory
@param String sshKeyFile
@param Map<String, DataSourceConfig> dataSources
@param Integer sqlMaxRows

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <script defer src="https://cdn.jsdelivr.net/npm/alpinejs@3.x.x/dist/cdn.min.js"></script>
    <title>Configuration</title>
</head>
<body class="p-8" x-data="{ showNewDataSourceForm: false, newDataSourceName: '' }">
<h1>Configuration</h1>
<form action="/config" method="post" class="config-form">
    Markdown Directory: ${markdownDirectory}<br>
    SSH Key File: ${sshKeyFile}

    <div class="form-group">
        <label for="sqlMaxRows">Max SQL Result Rows:</label>
        <input type="number" id="sqlMaxRows" name="sqlMaxRows" value="${sqlMaxRows}" class="config-input">
    </div>

    <h2>Database Configurations</h2>
    <div id="dataSourcesContainer">
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
    </div>

    <template x-if="showNewDataSourceForm">
        <div class="form-group">
            <h3 x-text="newDataSourceName"></h3>
            <label :for="newDataSourceName + '-url'">URL:</label>
            <input type="text" :id="newDataSourceName + '-url'" :name="'datasources[' + newDataSourceName + '].url'" class="config-input">

            <label :for="newDataSourceName + '-username'">Username:</label>
            <input type="text" :id="newDataSourceName + '-username'" :name="'datasources[' + newDataSourceName + '].username'" class="config-input">

            <label :for="newDataSourceName + '-password'">Password:</label>
            <input type="password" :id="newDataSourceName + '-password'" :name="'datasources[' + newDataSourceName + '].password'" class="config-input">

            <label :for="newDataSourceName + '-driverClassName'">Driver Class Name:</label>
            <input type="text" :id="newDataSourceName + '-driverClassName'" :name="'datasources[' + newDataSourceName + '].driverClassName'" class="config-input">
        </div>
    </template>

    <button type="submit" class="btn-blue-glow">
        <i class="fas fa-save"></i>Save And Reload
    </button>
</form>

<button @click="showNewDataSourceForm = true; newDataSourceName = prompt('Enter new datasource name:')" class="btn-blue-glow mt-4">
    <i class="fas fa-plus"></i>Add New Datasource
</button>

<a href="/" class="btn-blue-glow mt-4">
    <i class="fas fa-home"></i>Return to Home
</a>

</body>
</html>