package com.nova.tech.bank.sphere.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class ReferenceIdGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RANDOM_PART = 8;

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public ReferenceIdGenerator(Clock clock) {
        this.clock = clock;
    }

    public String next() {
        StringBuilder sb = new StringBuilder("TXN")
                .append(LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE))
                .append('-');
        for (int i = 0; i < RANDOM_PART; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}