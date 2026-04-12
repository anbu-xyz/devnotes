@param boolean keySet
@param String errorMessage
@param boolean decryptionFailed
@param String returnTo

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="/css/font-awesome.min.css">
    <script defer src="/js/alpine.min.js"></script>
    <title>Encryption Key</title>
</head>
<body>
<h1>Encryption Key</h1>

@if(keySet)
    <div class="encryption-status encryption-status-active">
        <i class="fas fa-lock"></i> Encryption passphrase is active. Datasource passwords are encrypted on disk.
    </div>

    <div class="form-actions" style="margin-top:1.5rem;">
        @if(returnTo != null && !returnTo.isEmpty())
            <a href="${returnTo}" class="btn-blue-glow">
                <i class="fas fa-arrow-right"></i>Return to page
            </a>
        @endif
        <a href="/config/encryption-key/change" class="btn-blue-glow">
            <i class="fas fa-key"></i>Change Passphrase
        </a>
        <a href="/config" class="btn-blue-glow">
            <i class="fas fa-arrow-left"></i>Back to Config
        </a>
    </div>
@else
    <div class="encryption-status encryption-status-inactive">
        <i class="fas fa-lock-open"></i> No passphrase is set. Datasource passwords are stored in plain text.
    </div>

    @if(errorMessage != null)
        <div class="encryption-error">
            <i class="fas fa-exclamation-triangle"></i> ${errorMessage}
        </div>
    @endif

    @if(decryptionFailed)
    <dialog id="decryption-failed-dialog" x-data x-init="$el.showModal()">
        <h2 class="decryption-failed-dialog-title">
            <i class="fas fa-exclamation-circle"></i> Incorrect Passphrase
        </h2>
        <p class="decryption-failed-dialog-body">
            The passphrase you entered could not decrypt the existing encrypted passwords on disk.
            This usually means a different passphrase was used when the passwords were originally encrypted.
        </p>
        <p class="decryption-failed-dialog-body">
            Please enter the correct passphrase to unlock your datasource configurations.
        </p>
        <div class="decryption-failed-dialog-actions">
            <button class="btn-blue-glow" @click="$el.closest('dialog').close()">
                <i class="fas fa-redo"></i> Try Again
            </button>
        </div>
    </dialog>
    @endif

    <form action="/config/encryption-key"
          method="post" class="config-form"
          x-data="{ passphrase: '', confirm: '' }">

        @if(returnTo != null && !returnTo.isEmpty())
            <input type="hidden" name="returnTo" value="${returnTo}">
        @endif

        <div class="config-section">
            <h2>Activate Encryption</h2>

            <p class="encryption-hint">
                Choose a memorable phrase of at least 12 characters -
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
        </div>

        <div class="form-actions">
            <button type="submit" class="btn-blue-glow" :disabled="passphrase.length < 12">
                <i class="fas fa-key"></i>Activate
            </button>
            <a href="/config" class="btn-blue-glow">
                <i class="fas fa-arrow-left"></i>Back to Config
            </a>
        </div>

    </form>
@endif
</body>
</html>

