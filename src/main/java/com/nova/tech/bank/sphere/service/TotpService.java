package com.nova.tech.bank.sphere.service;

import com.nova.tech.bank.sphere.util.Base32;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.OptionalLong;

@Service
@RequiredArgsConstructor
public class TotpService {

    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;
    /** Accept the previous, current and next step: tolerates about 30 s of clock drift. */
    public static final int WINDOW = 1;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Clock clock;

    /** 20 random bytes (160 bits) as Base32. This is the shared secret. */
    public String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return Base32.encode(bytes);
    }

    /** The link a QR code carries. Authenticator apps read it when scanned. */
    public String buildOtpAuthUri(String issuer, String accountEmail, String base32Secret) {
        return "otpauth://totp/" + enc(issuer) + ":" + enc(accountEmail)
                + "?secret=" + base32Secret
                + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS
                + "&period=" + PERIOD_SECONDS;
    }

    public long currentStep() {
        return clock.millis() / 1000 / PERIOD_SECONDS;
    }

    /** The code the authenticator would show right now (used by tests and the dev endpoint). */
    public String currentCode(String base32Secret) {
        return hotp(Base32.decode(base32Secret), currentStep(), DIGITS);
    }


    public OptionalLong verify(String base32Secret, String code, long lastUsedStep) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        byte[] key = Base32.decode(base32Secret);
        byte[] typed = code.getBytes(StandardCharsets.US_ASCII);
        long current = currentStep();

        long matched = -1;
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            byte[] expected = hotp(key, step, DIGITS).getBytes(StandardCharsets.US_ASCII);
            // Constant-time compare, and we never exit early, so timing reveals nothing.
            boolean equal = MessageDigest.isEqual(expected, typed);
            if (equal && step > lastUsedStep) {
                matched = step;
            }
        }
        return matched >= 0 ? OptionalLong.of(matched) : OptionalLong.empty();
    }

    /** HOTP from RFC 4226. TOTP is just HOTP with the time step as the counter. */
    static String hotp(byte[] key, long counter, int digits) {
        try {
            byte[] message = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(message);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    |  (hash[offset + 3] & 0xFF);

            int modulus = 1;
            for (int i = 0; i < digits; i++) modulus *= 10;

            return String.format("%0" + digits + "d", binary % modulus);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 not available", e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
