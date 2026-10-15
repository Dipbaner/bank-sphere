package com.nova.tech.bank.sphere.service;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class CryptoServiceTest {

    private static String key(int fill) {
        byte[] k = new byte[32];
        Arrays.fill(k, (byte) fill);
        return Base64.getEncoder().encodeToString(k);
    }

    private final CryptoService crypto = new CryptoService(key(7));

    @Test
    void encryptThenDecrypt_returnsOriginal() {
        String enc = crypto.encrypt("JBSWY3DPEHPK3PXP", "otp-secret:5");
        assertEquals("JBSWY3DPEHPK3PXP", crypto.decrypt(enc, "otp-secret:5"));
    }

    @Test
    void ciphertext_hidesPlaintext_andDiffersEachTime() {
        String a = crypto.encrypt("JBSWY3DPEHPK3PXP", "otp-secret:5");
        String b = crypto.encrypt("JBSWY3DPEHPK3PXP", "otp-secret:5");
        assertFalse(a.contains("JBSWY3DP"));
        assertNotEquals(a, b);                       // random IV
    }

    @Test
    void decrypt_failsForADifferentOwner() {
        String enc = crypto.encrypt("SECRET", "otp-secret:5");
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(enc, "otp-secret:6"));
    }

    @Test
    void decrypt_failsForTamperedData() {
        byte[] raw = Base64.getDecoder().decode(crypto.encrypt("SECRET", "otp-secret:5"));
        raw[raw.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(tampered, "otp-secret:5"));
    }

    @Test
    void decrypt_failsWithAnotherKey() {
        String enc = crypto.encrypt("SECRET", "otp-secret:5");
        CryptoService other = new CryptoService(key(9));
        assertThrows(IllegalStateException.class, () -> other.decrypt(enc, "otp-secret:5"));
    }

    @Test
    void constructor_rejectsWrongKeyLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new CryptoService(shortKey));
    }

    @Test
    void hmac_isDeterministic_andMessageSensitive() {
        assertEquals(crypto.hmac("backup:1:ABCDE12345"), crypto.hmac("backup:1:ABCDE12345"));
        assertNotEquals(crypto.hmac("backup:1:ABCDE12345"), crypto.hmac("backup:2:ABCDE12345"));
        assertEquals(64, crypto.hmac("x").length());
        assertNotEquals(new CryptoService(key(9)).hmac("x"), crypto.hmac("x"));
    }
}
