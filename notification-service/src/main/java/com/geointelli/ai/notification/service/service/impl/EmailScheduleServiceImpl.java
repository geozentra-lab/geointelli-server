package com.geointelli.ai.notification.service.service.impl;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import org.springframework.stereotype.Service;
import com.geointelli.ai.notification.service.entity.EmailCadence;
import com.geointelli.ai.notification.service.service.EmailScheduleService;

/**
 * Daily digests run at the next UTC midnight; weekly digests at the next Monday
 * UTC midnight.
 */
@Service
public class EmailScheduleServiceImpl implements EmailScheduleService {
    @Override
    public Instant next(EmailCadence cadence, Instant now) {
        if (cadence == EmailCadence.IMMEDIATE)
            return now;
        var today = now.atZone(ZoneOffset.UTC).toLocalDate();
        var next = cadence == EmailCadence.DAILY ? today.plusDays(1)
                : today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return next.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
