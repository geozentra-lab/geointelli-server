package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.AdminCoverageHealthDTO;

/**
 * Builds the admin coverage and provider-health dashboard (SCRUM-92) from recorded evidence only: data-quality
 * findings, source rows and recorded jobs. Read-only; no provider is called.
 */
public interface AdminCoverageHealthService {

    /**
     * Returns the quality counts, the source coverage and the health of each supported provider, with the time each
     * was observed. A block that cannot be read, or has nothing to measure, is returned as UNAVAILABLE with a
     * reason instead of a default value.
     *
     * @return the dashboard
     */
    AdminCoverageHealthDTO getDashboard();
}
