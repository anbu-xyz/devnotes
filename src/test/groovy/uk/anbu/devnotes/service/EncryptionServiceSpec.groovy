package uk.anbu.devnotes.service

import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class EncryptionServiceSpec extends Specification {

    // One shared service + salt so we only pay the PBKDF2 cost once for most tests
    @Shared
    EncryptionService service

    @Shared
    Path sharedSaltFile

    def setupSpec() {
        def tmpDir = Files.createTempDirectory("enc-spec-shared")
        sharedSaltFile = tmpDir.resolve("encryption.salt")
        service = new EncryptionService()
        service.setPassphrase("shared-passphrase-for-spec", sharedSaltFile)
    }

    def "isKeySet returns false initially and true after a valid setPassphrase call"() {
        given:
        def fresh = new EncryptionService()
        def saltFile = Files.createTempDirectory("enc-fresh-test").resolve("salt.hex")

        expect:
        !fresh.isKeySet()

        when:
        fresh.setPassphrase("another-fresh-passphrase", saltFile)

        then:
        fresh.isKeySet()
    }

    def "encrypt then decrypt returns original plaintext"() {
        when:
        def token = service.encrypt("super-secret-password")
        def plain = service.decrypt(token)

        then:
        plain == "super-secret-password"
    }

    def "two encryptions of the same plaintext produce different tokens"() {
        when:
        def token1 = service.encrypt("password")
        def token2 = service.encrypt("password")

        then:
        token1 != token2              // different random IVs
        token1.startsWith("ENC(")
        token2.startsWith("ENC(")
    }

    def "decrypt of a plain-text value returns it unchanged"() {
        expect:
        service.decrypt("plainPassword123") == "plainPassword123"
        service.decrypt("") == ""
    }

    def "isEncrypted returns true only for ENC(...) tokens"() {
        expect:
        EncryptionService.isEncrypted("ENC(abc)")
        !EncryptionService.isEncrypted("plaintext")
        !EncryptionService.isEncrypted("")
        !EncryptionService.isEncrypted(null)
        !EncryptionService.isEncrypted("ENC(abc")    // missing closing )
    }

    def "setPassphrase rejects a passphrase shorter than MIN_PASSPHRASE_LEN characters"() {
        given:
        def fresh = new EncryptionService()
        def saltFile = Files.createTempFile("salt-short", ".hex")
        def shortPassphrase = "tooshort"

        when:
        fresh.setPassphrase(shortPassphrase, saltFile)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("${EncryptionService.MIN_PASSPHRASE_LEN}")
    }

    def "encrypt throws IllegalStateException when no passphrase is set"() {
        given:
        def fresh = new EncryptionService()

        when:
        fresh.encrypt("value")

        then:
        thrown(IllegalStateException)
    }

    def "decrypt throws IllegalStateException when passphrase is not set and value is encrypted"() {
        given:
        def fresh = new EncryptionService()
        def token = service.encrypt("some-value")   // encrypted by the shared service

        when:
        fresh.decrypt(token)

        then:
        thrown(IllegalStateException)
    }

    def "same passphrase with the same salt file produces a key that can decrypt the other's ciphertext"() {
        given: "a second service instance initialised with the same passphrase and salt file"
        def service2 = new EncryptionService()
        service2.setPassphrase("shared-passphrase-for-spec", sharedSaltFile)

        when:
        def token = service.encrypt("hello-world")
        def plain = service2.decrypt(token)

        then:
        plain == "hello-world"
    }
}

