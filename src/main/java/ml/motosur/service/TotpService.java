package ml.motosur.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/** Double vérification (code à 6 chiffres qui change toutes les 30 s, norme TOTP / RFC 6238). */
@Service
public class TotpService {
    private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Nouvelle clé secrète de 160 bits, en base 32 (32 caractères). */
    public String newSecret() {
        byte[] b = new byte[20];
        RANDOM.nextBytes(b);
        return base32(b);
    }

    static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte x : data) {
            buffer = (buffer << 8) | (x & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(B32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
            buffer &= (1 << bits) - 1;
        }
        if (bits > 0) sb.append(B32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] unbase32(String s) {
        byte[] out = new byte[s.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int idx = 0;
        for (int i = 0; i < s.length(); i++) {
            int v = B32.indexOf(s.charAt(i));
            if (v < 0) throw new IllegalArgumentException("base32");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out[idx++] = (byte) ((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
                buffer &= (1 << bits) - 1;
            }
        }
        return out;
    }

    static int code(byte[] key, long step) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
        int off = h[h.length - 1] & 0x0F;
        int bin = ((h[off] & 0x7F) << 24) | ((h[off + 1] & 0xFF) << 16) | ((h[off + 2] & 0xFF) << 8) | (h[off + 3] & 0xFF);
        return bin % 1_000_000;
    }

    /**
     * Vérifie un code saisi. Tolère un pas de 30 s avant ou après (horloge du téléphone) et refuse un pas déjà
     * utilisé (un code ne sert qu'une fois). Renvoie le pas accepté, ou -1 si le code est faux.
     */
    public long verify(String secret, String input, long lastStep) {
        if (secret == null || input == null) return -1;
        String digits = input.replaceAll("\\s", "");
        if (!digits.matches("\\d{6}")) return -1;
        try {
            byte[] key = unbase32(secret);
            long now = System.currentTimeMillis() / 30_000L;
            for (long s = now - 1; s <= now + 1; s++) {
                if (s <= lastStep) continue;
                String expected = String.format("%06d", code(key, s));
                if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), digits.getBytes(StandardCharsets.UTF_8))) {
                    return s;
                }
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return -1;
        }
        return -1;
    }
}
