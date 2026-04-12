@import uk.anbu.devnotes.controller.JwtToolController.JwtParseResult
@param JwtParseResult result

@if(result.error() != null)
    <div class="encryption-error" style="margin-top:1.5rem">
        <i class="fas fa-exclamation-triangle"></i> <strong>Parse Error:</strong> ${result.error()}
    </div>
@else
    <div class="jwt-sections">

        <%-- ── Header ── --%>
        <div class="jwt-section jwt-section-header">
            <div class="jwt-section-title">
                <i class="fas fa-info-circle"></i> Header
                @if(result.algorithm() != null)
                    <span class="jwt-alg-badge">${result.algorithm()}</span>
                @endif
            </div>
            <pre class="jwt-json">${result.headerJson()}</pre>
        </div>

        <%-- ── Payload ── --%>
        <div class="jwt-section jwt-section-payload">
            <div class="jwt-section-title">
                <i class="fas fa-list-alt"></i> Payload
            </div>

            @if(result.issuedAt() != null || result.expiresAt() != null)
                <div class="jwt-claims-summary">
                    @if(result.issuedAt() != null)
                        <div class="jwt-claim-row">
                            <span class="jwt-claim-label"><i class="fas fa-clock"></i> Issued At</span>
                            <span class="jwt-claim-value">${result.issuedAt()}</span>
                        </div>
                    @endif
                    @if(result.expiresAt() != null)
                        <div class="jwt-claim-row ${result.expired() ? "jwt-claim-expired" : "jwt-claim-valid"}">
                            <span class="jwt-claim-label"><i class="fas fa-hourglass-end"></i> Expires At</span>
                            <span class="jwt-claim-value">
                                ${result.expiresAt()}
                                @if(result.expired())
                                    <span class="jwt-status-badge jwt-status-expired">EXPIRED</span>
                                @else
                                    <span class="jwt-status-badge jwt-status-valid">VALID</span>
                                @endif
                            </span>
                        </div>
                    @endif
                </div>
            @endif

            <pre class="jwt-json">${result.payloadJson()}</pre>
        </div>

        <%-- ── Signature ── --%>
        <div class="jwt-section jwt-section-signature">
            <div class="jwt-section-title">
                <i class="fas fa-fingerprint"></i> Signature
            </div>

            @if(result.validation() != null)
                @if(result.validation().valid())
                    <div class="jwt-validation-result jwt-validation-ok">
                        <i class="fas fa-check-circle"></i> ${result.validation().message()}
                    </div>
                @else
                    <div class="jwt-validation-result jwt-validation-fail">
                        <i class="fas fa-times-circle"></i> ${result.validation().message()}
                    </div>
                @endif
            @else
                <div class="jwt-validation-result jwt-validation-unknown">
                    <i class="fas fa-question-circle"></i> Signature not verified - no key or secret provided
                </div>
            @endif

            <div class="jwt-signature-box">
                <span class="jwt-claim-label">Raw (Base64url)</span>
                <code class="jwt-signature-value">${result.signatureBase64()}</code>
            </div>
        </div>

    </div>
@endif

