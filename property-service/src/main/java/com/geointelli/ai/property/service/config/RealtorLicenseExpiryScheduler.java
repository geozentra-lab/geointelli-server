package com.geointelli.ai.property.service.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import com.geointelli.ai.property.service.service.RoleGrantService;

/**
 * Daily revocation of the realtor role once the user's verified license has expired (SCRUM-204). Enabled with
 * {@code role-grants.realtor-license-expiry.enabled=true}; it enables scheduling itself, so it does not depend on
 * alerts.enabled. {@code POST /api/admin/role-grants/realtor-license-expiry} runs the same check on demand.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "role-grants.realtor-license-expiry", name = "enabled", havingValue = "true")
public class RealtorLicenseExpiryScheduler {

    private final RoleGrantService roleGrants;

    @Scheduled(cron = "${role-grants.realtor-license-expiry.cron:0 15 3 * * *}", zone = "UTC")
    public void revokeExpiredRealtorGrants() {
        try {
            roleGrants.revokeExpiredRealtorGrants();
        } catch (RuntimeException failure) {
            log.warn("Realtor license expiry run failed: {}", failure.getClass().getSimpleName());
        }
    }
}
