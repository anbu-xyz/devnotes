<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="/css/font-awesome.min.css">
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <script src="/js/htmx.min.js"></script>
    <script defer src="/js/alpine.min.js"></script>
    <title>JWT Parser</title>
</head>
<body class="p-8" x-data="{ showValidation: false }">

<h1><i class="fas fa-key"></i> JWT Token Parser</h1>
<p class="muted">Decode and inspect JWT tokens. Optionally validate the signature with a public key or HMAC secret.</p>

<form hx-post="/tools/jwt/parse"
      hx-target="#jwt-result"
      hx-swap="innerHTML"
      hx-indicator="#jwt-spinner">

    <div class="form-group">
        <label for="token">JWT Token:</label>
        <textarea id="token"
                  name="token"
                  class="config-input jwt-token-input"
                  rows="4"
                  placeholder="Paste your JWT token here - eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0...."
                  required></textarea>
    </div>

    <div class="form-group">
        <label class="jwt-checkbox-label">
            <input type="checkbox" x-model="showValidation">
            <span>Validate Signature</span>
        </label>
    </div>

    <div x-show="showValidation" x-cloak class="jwt-validation-inputs">
        <div class="jwt-key-tabs" x-data="{ activeTab: 'pubkey' }">
            <div class="jwt-tab-bar">
                <button type="button"
                        class="jwt-tab-btn"
                        :class="{ 'jwt-tab-active': activeTab === 'pubkey' }"
                        @click="activeTab = 'pubkey'">
                    <i class="fas fa-lock"></i> Public Key (RSA / EC)
                </button>
                <button type="button"
                        class="jwt-tab-btn"
                        :class="{ 'jwt-tab-active': activeTab === 'secret' }"
                        @click="activeTab = 'secret'">
                    <i class="fas fa-user-secret"></i> HMAC Secret (HS256 / HS384 / HS512)
                </button>
            </div>

            <div x-show="activeTab === 'pubkey'">
                <div class="form-group">
                    <label for="publicKey">Public Key (PEM format):</label>
                    <textarea id="publicKey"
                              name="publicKey"
                              class="config-input jwt-key-input"
                              rows="8"
                              placeholder="-----BEGIN PUBLIC KEY-----&#10;MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...&#10;-----END PUBLIC KEY-----"></textarea>
                    <small class="muted">Supports RS256, RS384, RS512, PS256, PS384, PS512, ES256, ES384, ES512</small>
                </div>
            </div>

            <div x-show="activeTab === 'secret'">
                <div class="form-group">
                    <label for="secret">HMAC Secret:</label>
                    <input type="text"
                           id="secret"
                           name="secret"
                           class="config-input"
                           placeholder="your-secret-key  (plain text or base64-encoded)">
                    <small class="muted">Accepts plain text or base64-encoded bytes. Both are tried automatically.</small>
                </div>
            </div>
        </div>
    </div>

    <div class="form-actions">
        <button type="submit" class="btn-blue-glow">
            <i class="fas fa-search"></i> Parse Token
        </button>
        <span id="jwt-spinner" class="htmx-indicator jwt-spinner">
            <i class="fas fa-spinner fa-spin"></i>
        </span>
    </div>
</form>

<div id="jwt-result" class="jwt-result-area"></div>

<hr>
<a href="/tools" class="btn-blue-glow"><i class="fas fa-arrow-left"></i> Back to Tools</a>

</body>
</html>

