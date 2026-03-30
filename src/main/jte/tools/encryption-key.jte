@param boolean keySet
@param String errorMessage

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <script defer src="https://cdn.jsdelivr.net/npm/alpinejs@3.x.x/dist/cdn.min.js"></script>
    <title>Encryption Key</title>
</head>
<body>
<h1>Encryption Key</h1>

@if(keySet)
    <div class="encryption-status encryption-status-active">
        <i class="fas fa-lock"></i> Encryption passphrase is active. Datasource passwords are encrypted on disk.
    </div>
@else
    <div class="encryption-status encryption-status-inactive">
        <i class="fas fa-lock-open"></i> No passphrase is set. Datasource passwords are stored in plain text.
    </div>
@endif

@if(errorMessage != null)
    <div class="encryption-error">
        <i class="fas fa-exclamation-triangle"></i> ${errorMessage}
    </div>
@endif

<form action="/config/encryption-key" method="post" class="config-form"
      x-data="{ passphrase: '', confirm: '', get mismatch() { return confirm.length > 0 && passphrase !== confirm } }">

    <div class="config-section">
        <h2>${keySet ? "Change Passphrase" : "Activate Encryption"}</h2>

        <p class="encryption-hint">
            Choose a memorable phrase of at least 12 characters —
            for example: <em>coffee-builds-faster-now</em> or <em>deploy on Fridays never</em>.
            The passphrase is <strong>never stored</strong>; you must re-enter it every time
            the server restarts.
        </p>

        <div class="form-group">
            <label for="passphrase">Passphrase:</label>
            <input type="password" id="passphrase" name="passphrase"
                   x-model="passphrase"
                   placeholder="at least 12 characters"
                   class="config-input" autocomplete="new-password">
        </div>

        <div class="form-group">
            <label for="confirm">Confirm passphrase:</label>
            <input type="password" id="confirm"
                   x-model="confirm"
                   placeholder="repeat passphrase"
                   class="config-input" autocomplete="new-password"
                   :class="mismatch ? 'config-input-error' : ''">
            <span class="encryption-mismatch" x-show="mismatch" x-cloak>
                <i class="fas fa-exclamation-circle"></i> Passphrases do not match
            </span>
        </div>
    </div>

    <div class="form-actions">
        <button type="submit" class="btn-blue-glow" :disabled="mismatch || passphrase.length < 12">
            <i class="fas fa-key"></i>Activate
        </button>
        <a href="/config" class="btn-blue-glow">
            <i class="fas fa-arrow-left"></i>Back to Config
        </a>
    </div>

</form>
</body>
</html>

