@param String errorMessage
@param boolean decryptionFailed
@param String returnTo

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="/generated/css/font-awesome.min.css">
    <script defer src="/generated/js/alpine.min.js"></script>
    <title>Change Encryption Passphrase</title>
</head>
<body>
<h1>Change Encryption Passphrase</h1>

<div class="encryption-status encryption-status-active">
    <i class="fas fa-lock"></i> A passphrase is currently active. Enter a new passphrase below to replace it.
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

<form action="/config/encryption-key/change"
      method="post" class="config-form"
      x-data="{ passphrase: '', confirm: '', get mismatch() { return confirm.length > 0 && passphrase !== confirm } }">

    @if(returnTo != null && !returnTo.isEmpty())
        <input type="hidden" name="returnTo" value="${returnTo}">
    @endif

    <div class="config-section">
        <h2>New Passphrase</h2>

        <p class="encryption-hint">
            Choose a memorable phrase of at least 12 characters -
            for example: <em>coffee-builds-faster-now</em> or <em>deploy on Fridays never</em>.
            The passphrase is <strong>never stored</strong>; you must re-enter it every time
            the server restarts.
        </p>

        <div class="form-group">
            <label for="passphrase">New Passphrase:</label>
            <input type="password" id="passphrase" name="passphrase"
                   x-model="passphrase"
                   placeholder="at least 12 characters"
                   class="config-input" autocomplete="new-password">
        </div>

        <div class="form-group">
            <label for="confirm">Confirm new passphrase:</label>
            <input type="password" id="confirm"
                   x-model="confirm"
                   placeholder="repeat new passphrase"
                   class="config-input" autocomplete="new-password"
                   :class="mismatch ? 'config-input-error' : ''">
            <span class="encryption-mismatch" x-show="mismatch" x-cloak>
                <i class="fas fa-exclamation-circle"></i> Passphrases do not match
            </span>
        </div>
    </div>

    <div class="form-actions">
        <button type="submit" class="btn-blue-glow" :disabled="mismatch || passphrase.length < 12">
            <i class="fas fa-key"></i>Change Passphrase
        </button>
        <a href="/config/encryption-key" class="btn-blue-glow">
            <i class="fas fa-arrow-left"></i>Back
        </a>
    </div>

</form>
</body>
</html>

