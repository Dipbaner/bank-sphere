package com.nova.tech.bank.sphere.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceIdGeneratorTest {

    private final ReferenceIdGenerator generator =
            new ReferenceIdGenerator(Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void hasTheExpectedFormat_andTodaysDate() {
        String id = generator.next();
        assertTrue(id.matches("^TXN\\d{8}-[A-HJ-NP-Z2-9]{8}$"), id);
        assertTrue(id.startsWith("TXN20261023-"));
        assertTrue(id.length() <= 30);
    }

    @Test
    void generatesDifferentIdsEachTime() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 5000; i++) ids.add(generator.next());
        assertEquals(5000, ids.size());
    }
}