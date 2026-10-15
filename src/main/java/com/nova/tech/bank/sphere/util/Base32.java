package com.nova.tech.bank.sphere.util;

public class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {}

    /** Encodes without '=' padding (what authenticator apps expect). */
    public static String encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    /** Accepts upper or lower case; ignores spaces, dashes and '='. */
    public static byte[] decode(String text) {
        String clean = text.toUpperCase().replaceAll("[\\s=-]", "");
        byte[] out = new byte[clean.length() * 5 / 8];
        int buffer = 0, bits = 0, index = 0;
        for (char c : clean.toCharArray()) {
            int val = ALPHABET.indexOf(c);
            if (val < 0) throw new IllegalArgumentException("Invalid Base32 character");
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                out[index++] = (byte) ((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out;
    }

}
