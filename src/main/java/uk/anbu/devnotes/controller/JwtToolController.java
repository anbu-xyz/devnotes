package uk.anbu.devnotes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class JwtToolController {

    private final TemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneId.of("UTC"));

    @GetMapping("/tools/jwt")
    public ResponseEntity<String> jwtPage() {
        var model = new HashMap<String, Object>();
        TemplateOutput output = new StringOutput();
        templateEngine.render("tools/jwt.jte", model, output);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(output.toString());
    }

    @PostMapping("/tools/jwt/parse")
    public ResponseEntity<String> parseJwt(
            @RequestParam String token,
            @RequestParam(required = false) String publicKey,
            @RequestParam(required = false) String secret) {
        var model = new HashMap<String, Object>();
        try {
            var result = parseJwtToken(token.trim(), publicKey, secret);
            model.put("result", result);
        } catch (Exception e) {
            log.debug("JWT parse error", e);
            model.put("result", new JwtParseResult(null, null, null, null, null, null, false, e.getMessage(), null));
        }
        TemplateOutput output = new StringOutput();
        templateEngine.render("tools/jwt-result.jte", model, output);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(output.toString());
    }

    private JwtParseResult parseJwtToken(String token, String publicKey, String secret) throws Exception {
        var parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Invalid JWT format: expected 3 dot-separated parts (header.payload.signature), got " + parts.length);
        }

        var decoder = Base64.getUrlDecoder();

        var headerBytes = decoder.decode(padBase64(parts[0]));
        var headerJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(objectMapper.readValue(headerBytes, Object.class));

        var payloadBytes = decoder.decode(padBase64(parts[1]));
        var payloadJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(objectMapper.readValue(payloadBytes, Object.class));

        var signatureBase64 = parts[2];

        @SuppressWarnings("unchecked")
        var headerMap = objectMapper.readValue(headerBytes, Map.class);
        var algorithm = (String) headerMap.get("alg");

        @SuppressWarnings("unchecked")
        var payloadMap = objectMapper.readValue(payloadBytes, Map.class);

        String expiresAt = null;
        String issuedAt = null;
        boolean expired = false;

        if (payloadMap.get("exp") instanceof Number expNum) {
            var expEpoch = expNum.longValue();
            expiresAt = DATE_FMT.format(Instant.ofEpochSecond(expEpoch));
            expired = Instant.now().getEpochSecond() > expEpoch;
        }
        if (payloadMap.get("iat") instanceof Number iatNum) {
            issuedAt = DATE_FMT.format(Instant.ofEpochSecond(iatNum.longValue()));
        }

        var signingInput = parts[0] + "." + parts[1];
        var signatureBytes = decoder.decode(padBase64(signatureBase64));

        ValidationResult validation = null;
        if (publicKey != null && !publicKey.isBlank()) {
            validation = validateWithPublicKey(algorithm, signingInput, signatureBytes, publicKey.trim());
        } else if (secret != null && !secret.isBlank()) {
            validation = validateWithSecret(algorithm, signingInput, signatureBytes, secret.trim());
        }

        return new JwtParseResult(algorithm, headerJson, payloadJson, signatureBase64,
                expiresAt, issuedAt, expired, null, validation);
    }

    private ValidationResult validateWithPublicKey(String algorithm, String signingInput,
                                                   byte[] signatureBytes, String pemKey) {
        try {
            var keyBytes = parsePemPublicKey(pemKey);
            var keyType = keyTypeForAlgorithm(algorithm);
            var kf = KeyFactory.getInstance(keyType);
            var publicKey = kf.generatePublic(new X509EncodedKeySpec(keyBytes));

            if (algorithm != null && algorithm.startsWith("PS")) {
                return validateRsaPss(algorithm, signingInput, signatureBytes, publicKey);
            }

            var javaAlg = javaAlgorithmForJwt(algorithm);
            var sig = Signature.getInstance(javaAlg);
            sig.initVerify(publicKey);
            sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
            var valid = sig.verify(signatureBytes);
            return new ValidationResult(valid, valid ? "Signature is valid ✓" : "Signature is invalid ✗");
        } catch (Exception e) {
            log.warn("Public key signature validation failed: {}", e.getMessage());
            return new ValidationResult(false, "Validation error: " + e.getMessage());
        }
    }

    private ValidationResult validateRsaPss(String algorithm, String signingInput,
                                            byte[] signatureBytes, PublicKey publicKey) throws Exception {
        var hashAlg = switch (algorithm) {
            case "PS256" -> "SHA-256";
            case "PS384" -> "SHA-384";
            case "PS512" -> "SHA-512";
            default -> throw new IllegalArgumentException("Unknown PS algorithm: " + algorithm);
        };
        var mgf1Spec = switch (algorithm) {
            case "PS256" -> MGF1ParameterSpec.SHA256;
            case "PS384" -> MGF1ParameterSpec.SHA384;
            case "PS512" -> MGF1ParameterSpec.SHA512;
            default -> throw new IllegalArgumentException("Unknown PS algorithm: " + algorithm);
        };
        var saltLen = switch (algorithm) {
            case "PS256" -> 32;
            case "PS384" -> 48;
            case "PS512" -> 64;
            default -> 32;
        };

        var sig = Signature.getInstance("RSASSA-PSS");
        sig.setParameter(new PSSParameterSpec(hashAlg, "MGF1", mgf1Spec, saltLen, 1));
        sig.initVerify(publicKey);
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        var valid = sig.verify(signatureBytes);
        return new ValidationResult(valid, valid ? "Signature is valid ✓" : "Signature is invalid ✗");
    }

    private ValidationResult validateWithSecret(String algorithm, String signingInput,
                                                byte[] signatureBytes, String secret) {
        try {
            if (algorithm == null || !algorithm.startsWith("HS")) {
                return new ValidationResult(false,
                        "Secret key validation only supports HS256/HS384/HS512 algorithms (token uses " + algorithm + ")");
            }
            var hmacAlg = switch (algorithm) {
                case "HS256" -> "HmacSHA256";
                case "HS384" -> "HmacSHA384";
                case "HS512" -> "HmacSHA512";
                default -> throw new IllegalArgumentException("Unknown HS algorithm: " + algorithm);
            };

            // Try as UTF-8 bytes first
            var secretBytesUtf8 = secret.getBytes(StandardCharsets.UTF_8);
            if (computeHmacAndVerify(hmacAlg, signingInput, secretBytesUtf8, signatureBytes)) {
                return new ValidationResult(true, "Signature is valid ✓");
            }

            // Try as base64-decoded bytes
            try {
                var secretBytesB64 = Base64.getDecoder().decode(secret);
                if (computeHmacAndVerify(hmacAlg, signingInput, secretBytesB64, signatureBytes)) {
                    return new ValidationResult(true, "Signature is valid ✓ (secret decoded as base64)");
                }
            } catch (IllegalArgumentException ignored) {
                // not valid base64, skip
            }

            return new ValidationResult(false, "Signature is invalid ✗");
        } catch (Exception e) {
            return new ValidationResult(false, "Validation error: " + e.getMessage());
        }
    }

    private boolean computeHmacAndVerify(String hmacAlg, String signingInput,
                                         byte[] secretBytes, byte[] expectedSig) throws Exception {
        var mac = Mac.getInstance(hmacAlg);
        mac.init(new SecretKeySpec(secretBytes, hmacAlg));
        var computed = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        return Arrays.equals(computed, expectedSig);
    }

    private byte[] parsePemPublicKey(String pem) {
        var cleaned = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(cleaned);
    }

    private String javaAlgorithmForJwt(String jwtAlg) {
        if (jwtAlg == null) return "SHA256withRSA";
        return switch (jwtAlg) {
            case "RS256" -> "SHA256withRSA";
            case "RS384" -> "SHA384withRSA";
            case "RS512" -> "SHA512withRSA";
            case "ES256" -> "SHA256withECDSA";
            case "ES384" -> "SHA384withECDSA";
            case "ES512" -> "SHA512withECDSA";
            default -> jwtAlg;
        };
    }

    private String keyTypeForAlgorithm(String jwtAlg) {
        if (jwtAlg != null && (jwtAlg.startsWith("ES"))) return "EC";
        return "RSA";
    }

    private static String padBase64(String base64url) {
        return switch (base64url.length() % 4) {
            case 2 -> base64url + "==";
            case 3 -> base64url + "=";
            default -> base64url;
        };
    }

    public record JwtParseResult(
            String algorithm,
            String headerJson,
            String payloadJson,
            String signatureBase64,
            String expiresAt,
            String issuedAt,
            boolean expired,
            String error,
            ValidationResult validation
    ) {}

    public record ValidationResult(boolean valid, String message) {}
}

