package uk.anbu.devnotes.controller

import com.fasterxml.jackson.databind.ObjectMapper
import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.resolve.DirectoryCodeResolver
import org.jsoup.Jsoup
import org.springframework.http.HttpStatus
import spock.lang.Specification

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64

class JwtToolControllerSpec extends Specification {

    JwtToolController controller
    TemplateEngine templateEngine
    ObjectMapper objectMapper = new ObjectMapper()

    def setup() {
        def codeResolver = new DirectoryCodeResolver(Paths.get("src/main/jte"))
        templateEngine = TemplateEngine.create(codeResolver, Paths.get("src/main/jte"), ContentType.Html)
        controller = new JwtToolController(templateEngine, objectMapper)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String encodeBase64Url(byte[] bytes) {
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private String encodeBase64Url(Map payload) {
        encodeBase64Url(objectMapper.writeValueAsBytes(payload))
    }

    private String buildUnsignedJwt(Map header, Map payload) {
        def h = encodeBase64Url(header)
        def p = encodeBase64Url(payload)
        "$h.$p"
    }

    private String buildHmacJwt(String secret, String alg = "HS256", Map extraPayload = [:]) {
        def header = [alg: alg, typ: "JWT"]
        def payload = [sub: "user123", iss: "test"] + extraPayload
        def signingInput = buildUnsignedJwt(header, payload)

        def hmacAlg = [HS256: "HmacSHA256", HS384: "HmacSHA384", HS512: "HmacSHA512"][alg]
        def mac = Mac.getInstance(hmacAlg)
        mac.init(new SecretKeySpec(secret.bytes, hmacAlg))
        def sig = encodeBase64Url(mac.doFinal(signingInput.bytes))
        "$signingInput.$sig"
    }

    private Map buildRsaJwt(String alg = "RS256") {
        def kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        def kp = kpg.generateKeyPair()

        def header = [alg: alg, typ: "JWT"]
        def payload = [sub: "user123", iss: "test"]
        def signingInput = buildUnsignedJwt(header, payload)

        def javaAlg = [RS256: "SHA256withRSA", RS384: "SHA384withRSA", RS512: "SHA512withRSA"][alg]
        def sig = Signature.getInstance(javaAlg)
        sig.initSign(kp.private)
        sig.update(signingInput.bytes)
        def signedBytes = encodeBase64Url(sig.sign())

        def pemKey = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getMimeEncoder(64, "\n".bytes).encodeToString(kp.public.encoded) +
                "\n-----END PUBLIC KEY-----"

        [token: "$signingInput.$signedBytes", publicKey: pemKey]
    }

    private Map buildEcJwt(String alg = "ES256") {
        def curveName = [ES256: "secp256r1", ES384: "secp384r1", ES512: "secp521r1"][alg]
        def kpg = KeyPairGenerator.getInstance("EC")
        def spec = new java.security.spec.ECGenParameterSpec(curveName)
        kpg.initialize(spec)
        def kp = kpg.generateKeyPair()

        def header = [alg: alg, typ: "JWT"]
        def payload = [sub: "user123", iss: "test"]
        def signingInput = buildUnsignedJwt(header, payload)

        def javaAlg = [ES256: "SHA256withECDSA", ES384: "SHA384withECDSA", ES512: "SHA512withECDSA"][alg]
        def sig = Signature.getInstance(javaAlg)
        sig.initSign(kp.private)
        sig.update(signingInput.bytes)
        def signedBytes = encodeBase64Url(sig.sign())

        def pemKey = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getMimeEncoder(64, "\n".bytes).encodeToString(kp.public.encoded) +
                "\n-----END PUBLIC KEY-----"

        [token: "$signingInput.$signedBytes", publicKey: pemKey]
    }

    // ── GET /tools/jwt ───────────────────────────────────────────────────────

    def "GET /tools/jwt renders the JWT parser page"() {
        when:
        def response = controller.jwtPage()

        then:
        response.statusCode == HttpStatus.OK
        response.headers.getContentType().toString().contains("text/html")
        def doc = Jsoup.parse(response.body)
        doc.select("title").text() == "JWT Parser"
        doc.select("textarea#token").size() == 1
        doc.select("button[type=submit]").text().contains("Parse Token")
    }

    // ── POST /tools/jwt/parse - basic parsing ────────────────────────────────

    def "parseJwt() decodes header and payload of an HS256 token without validation"() {
        given:
        def token = buildHmacJwt("secret")

        when:
        def response = controller.parseJwt(token, null, null)

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.select(".jwt-section-header").size() == 1
        doc.select(".jwt-section-payload").size() == 1
        doc.select(".jwt-section-signature").size() == 1
        doc.select(".jwt-alg-badge").text() == "HS256"
        doc.select(".jwt-json").first().text().contains("HS256")
        doc.select(".jwt-validation-unknown").size() == 1
    }

    def "parseJwt() returns an error block for a token with fewer than 3 parts"() {
        given:
        def token = "notAValidJwt"

        when:
        def response = controller.parseJwt(token, null, null)

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.select(".encryption-error").size() == 1
        doc.select(".encryption-error").text().contains("Invalid JWT format")
    }

    def "parseJwt() returns an error block when header is not valid base64url JSON"() {
        given:
        def token = "!!!.eyJzdWIiOiJ1c2VyIn0.sig"

        when:
        def response = controller.parseJwt(token, null, null)

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.select(".encryption-error").size() == 1
    }

    def "parseJwt() shows issued-at and expiry dates when iat and exp claims are present"() {
        given:
        def futureExp = Instant.now().plusSeconds(3600).epochSecond
        def iat = Instant.now().minusSeconds(600).epochSecond
        def token = buildHmacJwt("secret", "HS256", [iat: iat, exp: futureExp])

        when:
        def response = controller.parseJwt(token, null, null)

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.select(".jwt-claims-summary").size() == 1
        doc.select(".jwt-claim-row").size() == 2
        doc.select(".jwt-status-valid").size() == 1
        doc.select(".jwt-status-expired").size() == 0
    }

    def "parseJwt() marks a token with a past exp as expired"() {
        given:
        def pastExp = Instant.now().minusSeconds(3600).epochSecond
        def token = buildHmacJwt("secret", "HS256", [exp: pastExp])

        when:
        def response = controller.parseJwt(token, null, null)

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.select(".jwt-status-expired").size() == 1
        doc.select(".jwt-status-valid").size() == 0
    }

    // ── HMAC secret validation ────────────────────────────────────────────────

    def "parseJwt() validates HS256 signature with the correct secret"() {
        given:
        def secret = "my-super-secret"
        def token = buildHmacJwt(secret, "HS256")

        when:
        def response = controller.parseJwt(token, null, secret)

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.select(".jwt-validation-ok").size() == 1
        doc.select(".jwt-validation-ok").text().contains("valid")
        doc.select(".jwt-validation-fail").size() == 0
    }

    def "parseJwt() rejects HS256 signature with an incorrect secret"() {
        given:
        def token = buildHmacJwt("correct-secret", "HS256")

        when:
        def response = controller.parseJwt(token, null, "wrong-secret")

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.select(".jwt-validation-fail").size() == 1
        doc.select(".jwt-validation-ok").size() == 0
    }

    def "parseJwt() validates HS384 and HS512 signatures with correct secrets"(String alg) {
        given:
        def secret = "another-secret-key"
        def token = buildHmacJwt(secret, alg)

        when:
        def response = controller.parseJwt(token, null, secret)

        then:
        response.statusCode == HttpStatus.OK
        Jsoup.parse(response.body).select(".jwt-validation-ok").size() == 1

        where:
        alg << ["HS384", "HS512"]
    }

    def "parseJwt() returns validation error when HS secret is supplied for RS256 token"() {
        given:
        def rsaJwt = buildRsaJwt("RS256")

        when:
        def response = controller.parseJwt(rsaJwt.token as String, null, "some-secret")

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.select(".jwt-validation-fail").size() == 1
        doc.select(".jwt-validation-fail").text().contains("RS256")
    }

    // ── RSA public key validation ─────────────────────────────────────────────

    def "parseJwt() validates RS256 signature with correct RSA public key"() {
        given:
        def jwt = buildRsaJwt("RS256")

        when:
        def response = controller.parseJwt(jwt.token as String, jwt.publicKey as String, null)

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.select(".jwt-validation-ok").size() == 1
        doc.select(".jwt-validation-ok").text().contains("valid")
    }

    def "parseJwt() rejects RS256 signature with a different RSA public key"() {
        given:
        def jwt = buildRsaJwt("RS256")
        def wrongJwt = buildRsaJwt("RS256")   // different key pair

        when:
        def response = controller.parseJwt(jwt.token as String, wrongJwt.publicKey as String, null)

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.select(".jwt-validation-fail").size() == 1
        doc.select(".jwt-validation-ok").size() == 0
    }

    def "parseJwt() validates RS512 signature with correct RSA public key"() {
        given:
        def jwt = buildRsaJwt("RS512")

        when:
        def response = controller.parseJwt(jwt.token as String, jwt.publicKey as String, null)

        then:
        response.statusCode == HttpStatus.OK
        Jsoup.parse(response.body).select(".jwt-validation-ok").size() == 1
    }

    // ── EC public key validation ──────────────────────────────────────────────

    def "parseJwt() validates ES256 signature with correct EC public key"() {
        given:
        def jwt = buildEcJwt("ES256")

        when:
        def response = controller.parseJwt(jwt.token as String, jwt.publicKey as String, null)

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.select(".jwt-validation-ok").size() == 1
        doc.select(".jwt-alg-badge").text() == "ES256"
    }

    def "parseJwt() rejects ES256 signature with a different EC public key"() {
        given:
        def jwt = buildEcJwt("ES256")
        def wrongJwt = buildEcJwt("ES256")

        when:
        def response = controller.parseJwt(jwt.token as String, wrongJwt.publicKey as String, null)

        then:
        response.statusCode == HttpStatus.OK
        Jsoup.parse(response.body).select(".jwt-validation-fail").size() == 1
    }

    def "parseJwt() validates ES384 and ES512 signatures with correct EC public keys"(String alg) {
        given:
        def jwt = buildEcJwt(alg)

        when:
        def response = controller.parseJwt(jwt.token as String, jwt.publicKey as String, null)

        then:
        response.statusCode == HttpStatus.OK
        Jsoup.parse(response.body).select(".jwt-validation-ok").size() == 1

        where:
        alg << ["ES384", "ES512"]
    }

    // ── signature section displays raw base64url ──────────────────────────────

    def "parseJwt() displays the raw signature base64url in the signature section"() {
        given:
        def token = buildHmacJwt("secret")
        def rawSig = token.split("\\.")[2]

        when:
        def response = controller.parseJwt(token, null, null)

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.select(".jwt-signature-value").text() == rawSig
    }

    // ── algorithm badge ───────────────────────────────────────────────────────

    def "parseJwt() shows the algorithm badge from the header for various algorithms"(String alg) {
        given:
        def token = buildHmacJwt("secret", alg)

        when:
        def response = controller.parseJwt(token, null, null)

        then:
        Jsoup.parse(response.body).select(".jwt-alg-badge").text() == alg

        where:
        alg << ["HS256", "HS384", "HS512"]
    }

    // ── malformed public key ──────────────────────────────────────────────────

    def "parseJwt() returns a validation-fail block when public key is not valid PEM"() {
        given:
        def token = buildHmacJwt("secret", "HS256")

        when:
        def response = controller.parseJwt(token, "this-is-not-a-pem-key", null)

        then:
        response.statusCode == HttpStatus.OK
        def doc = Jsoup.parse(response.body)
        doc.select(".jwt-validation-fail").size() == 1
        doc.select(".jwt-validation-fail").text().contains("Validation error")
    }
}

