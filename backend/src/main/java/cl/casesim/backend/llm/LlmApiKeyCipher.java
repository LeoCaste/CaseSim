package cl.casesim.backend.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class LlmApiKeyCipher {

    private static final String PREFIX = "v1:";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final byte[] keyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public LlmApiKeyCipher(@Value("${casesim.security.llm-key}") String secret) {
        this.keyBytes = deriveKey(secret);
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return null;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] encrypted = cipher.doFinal(plainText.trim().getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);

            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("No fue posible cifrar la API key.", ex);
        }
    }

    public String decrypt(String encryptedValue) {
        if (!StringUtils.hasText(encryptedValue)) {
            return null;
        }

        if (!encryptedValue.startsWith(PREFIX)) {
            return encryptedValue;
        }

        try {
            byte[] payload = Base64.getDecoder().decode(encryptedValue.substring(PREFIX.length()));
            if (payload.length <= GCM_IV_LENGTH_BYTES) {
                return null;
            }

            byte[] iv = Arrays.copyOfRange(payload, 0, GCM_IV_LENGTH_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, GCM_IV_LENGTH_BYTES, payload.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] plainBytes = cipher.doFinal(encrypted);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }

    private byte[] deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(hash, 16);
        } catch (Exception ex) {
            throw new IllegalStateException("No fue posible inicializar cifrado de API key.", ex);
        }
    }
}
