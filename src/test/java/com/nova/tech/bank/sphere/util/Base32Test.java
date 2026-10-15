package com.nova.tech.bank.sphere.util;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class Base32Test {

    private static byte[] b(String s) { return s.getBytes(StandardCharsets.US_ASCII); }

    @Test
    void encode_matchesRfc4648Vectors() {
        assertEquals("MY",         Base32.encode(b("f")));
        assertEquals("MZXQ",       Base32.encode(b("fo")));
        assertEquals("MZXW6",      Base32.encode(b("foo")));
        assertEquals("MZXW6YQ",    Base32.encode(b("foob")));
        assertEquals("MZXW6YTB",   Base32.encode(b("fooba")));
        assertEquals("MZXW6YTBOI", Base32.encode(b("foobar")));
    }

    @Test
    void decode_isInverseOfEncode() {
        assertArrayEquals(b("foobar"), Base32.decode("MZXW6YTBOI"));
        byte[] random = new byte[20];
        new java.security.SecureRandom().nextBytes(random);
        assertArrayEquals(random, Base32.decode(Base32.encode(random)));
    }

    @Test
    void decode_ignoresCaseSpacesAndPadding() {
        assertArrayEquals(b("foobar"), Base32.decode("mzxw 6ytb-oi======"));
    }

    @Test
    void decode_rejectsInvalidCharacters() {
        assertThrows(IllegalArgumentException.class, () -> Base32.decode("MZXW1!"));
    }
}
