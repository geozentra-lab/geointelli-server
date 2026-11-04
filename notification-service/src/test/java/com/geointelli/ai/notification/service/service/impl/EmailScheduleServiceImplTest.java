package com.geointelli.ai.notification.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.geointelli.ai.notification.service.entity.EmailCadence;

class EmailScheduleServiceImplTest {
    @ParameterizedTest
    @CsvSource({
            "IMMEDIATE,2026-10-30T09:00:00Z,2026-10-30T09:00:00Z",
            "DAILY,2026-10-30T09:00:00Z,2026-10-31T00:00:00Z",
            "DAILY,2026-10-31T00:00:00Z,2026-11-01T00:00:00Z",
            "WEEKLY,2026-10-30T09:00:00Z,2026-11-02T00:00:00Z",
            "WEEKLY,2026-11-01T23:59:59Z,2026-11-02T00:00:00Z",
            "WEEKLY,2026-11-02T00:00:00Z,2026-11-09T00:00:00Z" })
    void respectsUtcBoundaries(EmailCadence cadence, String from, String expected) {
        assertEquals(Instant.parse(expected), new EmailScheduleServiceImpl().next(cadence, Instant.parse(from)));
    }
}
