@import uk.anbu.devnotes.service.ConfigService.DataSourceConfig
@import java.util.Map

@param String markdownDirectory
@param String sshKeyFile
@param Map<String, DataSourceConfig> dataSources
@param Integer sqlMaxRows
@param String chromeDriverLocation
@param Boolean encryptionKeySet

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <script defer src="https://cdn.jsdelivr.net/npm/alpinejs@3.x.x/dist/cdn.min.js"></script>
    <title>Configuration</title>
</head>
<body x-data="{ openSources: {}, deleted: {}, showAddForm: false, newName: '' }">
<h1>Configuration</h1>

@if(!encryptionKeySet)
<div class="warning-banner">
    <i class="fas fa-lock-open"></i>
    Datasource passwords are stored in plain text.
    <a href="/config/encryption-key">Activate encryption</a> to protect them.
</div>
@else
<div class="encryption-status encryption-status-active" style="margin-bottom:1rem;">
    <i class="fas fa-lock"></i> Passwords are encrypted on disk.
    <a href="/config/encryption-key" style="margin-left:0.5rem;">Change passphrase</a>
</div>
@endif

<form action="/config" method="post" class="config-form">

    <div class="config-section">
        <h2>General Settings</h2>

        <div class="form-group">
            <label for="markdownDirectory">Markdown Directory:</label>
            <input type="text" id="markdownDirectory" value="${markdownDirectory}"
                   class="config-input config-readonly" readonly>
        </div>

        <div class="form-group">
            <label for="sshKeyFile">SSH Key File:</label>
            <input type="text" id="sshKeyFile" value="${sshKeyFile}"
                   class="config-input config-readonly" readonly>
        </div>

        <div class="form-group">
            <label for="chromeDriverLocation">Chrome Driver Location:</label>
            <input type="text" id="chromeDriverLocation" name="chromeDriverLocation"
                   value="${chromeDriverLocation}" class="config-input">
        </div>

        <div class="form-group">
            <label for="sqlMaxRows">Max SQL Result Rows:</label>
            <input type="number" id="sqlMaxRows" name="sqlMaxRows"
                   value="${sqlMaxRows}" class="config-input">
        </div>
    </div>

    <div class="config-section">
        <h2>Database Configurations</h2>

        @for(var entry : dataSources.entrySet())
            <div class="datasource-card" x-show="!deleted['${entry.getKey()}']">
                <input type="hidden" name="delete_datasource" value="${entry.getKey()}"
                       :disabled="!deleted['${entry.getKey()}']">
                <div class="datasource-card-header"
                     :class="{ 'datasource-card-header-open': openSources['${entry.getKey()}'] }"
                     @click="openSources['${entry.getKey()}'] = !openSources['${entry.getKey()}']">
                    <span class="datasource-card-name">${entry.getKey()}</span>
                    <div class="datasource-card-actions">
                        <button type="button" class="btn-blue-glow"
                                title="Delete datasource"
                                @click.stop="if(confirm('Delete datasource ${entry.getKey()}?')) deleted['${entry.getKey()}'] = true">
                            <i class="fas fa-trash"></i>
                        </button>
                        <i class="fas" :class="openSources['${entry.getKey()}'] ? 'fa-chevron-up' : 'fa-chevron-down'"></i>
                    </div>
                </div>
                <div class="datasource-card-body" x-show="openSources['${entry.getKey()}']" x-cloak>
                    <div class="form-group">
                        <label for="${entry.getKey()}-url">URL:</label>
                        <input type="text" id="${entry.getKey()}-url"
                               name="datasources[${entry.getKey()}].url"
                               value="${entry.getValue().url()}" class="config-input"
                               :disabled="deleted['${entry.getKey()}']">
                    </div>
                    <div class="form-group">
                        <label for="${entry.getKey()}-username">Username:</label>
                        <input type="text" id="${entry.getKey()}-username"
                               name="datasources[${entry.getKey()}].username"
                               value="${entry.getValue().username()}" class="config-input"
                               :disabled="deleted['${entry.getKey()}']">
                    </div>
                    <div class="form-group">
                        <label for="${entry.getKey()}-password">Password:</label>
                        <input type="password" id="${entry.getKey()}-password"
                               name="datasources[${entry.getKey()}].password"
                               value="********" class="config-input"
                               :disabled="deleted['${entry.getKey()}']">
                    </div>
                    <div class="form-group">
                        <label for="${entry.getKey()}-driverClassName">Driver Class Name:</label>
                        <input type="text" id="${entry.getKey()}-driverClassName"
                               name="datasources[${entry.getKey()}].driverClassName"
                               value="${entry.getValue().driverClassName()}" class="config-input"
                               :disabled="deleted['${entry.getKey()}']">
                    </div>
                </div>
            </div>
        @endfor

        <div class="datasource-card" x-show="showAddForm" x-cloak>
            <div class="datasource-card-header datasource-card-header-open">
                <span class="datasource-card-name"
                      x-text="newName ? newName : 'New Datasource'"></span>
            </div>
            <div class="datasource-card-body">
                <div class="form-group">
                    <label>Datasource Name:</label>
                    <input type="text" x-model="newName" class="config-input"
                           placeholder="e.g. myDatabase">
                </div>
                <div class="form-group">
                    <label :for="newName + '-new-url'">URL:</label>
                    <input type="text" :id="newName + '-new-url'"
                           :name="newName ? 'datasources[' + newName + '].url' : ''"
                           class="config-input" :disabled="!newName">
                </div>
                <div class="form-group">
                    <label :for="newName + '-new-username'">Username:</label>
                    <input type="text" :id="newName + '-new-username'"
                           :name="newName ? 'datasources[' + newName + '].username' : ''"
                           class="config-input" :disabled="!newName">
                </div>
                <div class="form-group">
                    <label :for="newName + '-new-password'">Password:</label>
                    <input type="password" :id="newName + '-new-password'"
                           :name="newName ? 'datasources[' + newName + '].password' : ''"
                           class="config-input" :disabled="!newName">
                </div>
                <div class="form-group">
                    <label :for="newName + '-new-driverClassName'">Driver Class Name:</label>
                    <input type="text" :id="newName + '-new-driverClassName'"
                           :name="newName ? 'datasources[' + newName + '].driverClassName' : ''"
                           class="config-input" :disabled="!newName">
                </div>
            </div>
        </div>
    </div>

    <div class="form-actions">
        <button type="submit" class="btn-blue-glow">
            <i class="fas fa-save"></i>Save &amp; Reload
        </button>
        <button type="button" class="btn-blue-glow"
                @click="showAddForm = !showAddForm; if(!showAddForm) newName = ''">
            <i class="fas fa-plus"></i>Add Datasource
        </button>
        <a href="/" class="btn-blue-glow">
            <i class="fas fa-home"></i>Home
        </a>
    </div>

</form>
</body>
</html>