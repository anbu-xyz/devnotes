package uk.anbu.devnotes.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM       = "AES/GCM/NoPadding";
    private static final String KDF             = "PBKDF2WithHmacSHA256";
    private static final int    IV_LEN          = 12;       // bytes
    private static final int    TAG_LEN_BITS    = 128;
    static final         int    KDF_ITERS       = 600_000;  // OWASP 2023 recommendation
    private static final int    KEY_BITS        = 256;
    private static final int    SALT_LEN        = 16;       // bytes
    static final         int    MIN_PASSPHRASE_LEN = 12;
    private static final String ENC_PREFIX      = "ENC(";
    private static final String ENC_SUFFIX      = ")";

    private volatile SecretKey secretKey = null;

    /** Returns {@code true} when a passphrase has been accepted and an AES key is active. */
    public boolean isKeySet() {
        return secretKey != null;
    }

    /**
     * Derives an AES-256 key from {@code passphrase} using PBKDF2WithHmacSHA256 and
     * a salt that is loaded from {@code saltFile} (or freshly generated and written there
     * on first use).  Runs a round-trip self-test before committing the new key.
     *
     * @throws IllegalArgumentException if the passphrase is shorter than {@link #MIN_PASSPHRASE_LEN}
     * @throws RuntimeException         if salt I/O or cipher operations fail
     */
    public void setPassphrase(String passphrase, Path saltFile) {
        if (passphrase == null || passphrase.length() < MIN_PASSPHRASE_LEN) {
            throw new IllegalArgumentException(
                "Passphrase must be at least " + MIN_PASSPHRASE_LEN + " characters");
        }
        byte[] salt;
        try {
            salt = loadOrCreateSalt(saltFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load or create encryption salt at " + saltFile, e);
        }
        char[] passphraseChars = passphrase.toCharArray();
        try {
            SecretKey derived = deriveKey(passphraseChars, salt);
            SecretKey previous = this.secretKey;
            this.secretKey = derived;
            // Round-trip validation — roll back if anything goes wrong
            try {
                String token = encrypt("devnotes-validation");
                String plain  = decrypt(token);
                if (!"devnotes-validation".equals(plain)) {
                    this.secretKey = previous;
                    throw new IllegalStateException("Encryption self-test failed after key derivation");
                }
            } catch (RuntimeException e) {
                this.secretKey = previous;
                throw e;
            }
        } finally {
            Arrays.fill(passphraseChars, '\0');
        }
        log.info("Encryption passphrase accepted; AES-256-GCM key is active");
    }

    /**
     * Reads the salt from {@code saltFile} if it exists; otherwise generates a fresh
     * 16-byte random salt, writes it as a hex string to {@code saltFile}, and returns it.
     */
    private byte[] loadOrCreateSalt(Path saltFile) throws IOException {
        if (Files.exists(saltFile)) {
            String hex = Files.readString(saltFile).strip();
            if (!hex.isEmpty()) {
                return HexFormat.of().parseHex(hex);
            }
            log.warn("Salt file {} is empty; a new salt will be generated", saltFile);
        }
        byte[] salt = new byte[SALT_LEN];
        new SecureRandom().nextBytes(salt);
        Files.createDirectories(saltFile.getParent());
        Files.writeString(saltFile, HexFormat.of().formatHex(salt));
        log.info("Created new encryption salt at {}", saltFile);
        return salt;
    }

    private SecretKey deriveKey(char[] passphrase, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, KDF_ITERS, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Failed to derive AES key via " + KDF, e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Encrypts {@code plaintext} and returns an {@code ENC(...)} token.
     *
     * @throws IllegalStateException if no passphrase has been set
     */
    public String encrypt(String plaintext) {
        if (secretKey == null) {
            throw new IllegalStateException(
                "No encryption passphrase is set; visit /config/encryption-key");
        }
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LEN_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            // Prepend IV so decrypt() can extract it without extra state
            byte[] combined = new byte[IV_LEN + ciphertext.length];
            System.arraycopy(iv,         0, combined, 0,      IV_LEN);
            System.arraycopy(ciphertext, 0, combined, IV_LEN, ciphertext.length);
            return ENC_PREFIX
                + Base64.getUrlEncoder().withoutPadding().encodeToString(combined)
                + ENC_SUFFIX;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                 | InvalidAlgorithmParameterException | IllegalBlockSizeException
                 | BadPaddingException e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts an {@code ENC(...)} token back to the original string.
     * If {@code value} does not start with {@code ENC(} it is returned unchanged
     * (backward-compatibility with plain-text passwords in existing files).
     *
     * @throws IllegalStateException if the value is encrypted but no passphrase has been set
     */
    public String decrypt(String value) {
        if (!isEncrypted(value)) {
            return value;
        }
        if (secretKey == null) {
            throw new IllegalStateException(
                "No encryption passphrase is set; visit /config/encryption-key");
        }
        try {
            String inner   = value.substring(ENC_PREFIX.length(), value.length() - ENC_SUFFIX.length());
            byte[] combined  = Base64.getUrlDecoder().decode(inner);
            byte[] iv        = Arrays.copyOfRange(combined, 0,      IV_LEN);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_LEN, combined.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LEN_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                 | InvalidAlgorithmParameterException | IllegalBlockSizeException
                 | BadPaddingException e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /** Returns {@code true} if {@code value} is an {@code ENC(...)} token. */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENC_PREFIX) && value.endsWith(ENC_SUFFIX);
    }
}

