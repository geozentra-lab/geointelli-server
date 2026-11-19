package com.geointelli.ai.property.service.service;

import java.util.Optional;

/**
 * FIPS codes that scope an environmental reference load (SCRUM-201), read from {@code counties.fips_code}
 * (migration V42). A flood zone file belongs to one county; the statewide files to one state.
 */
public interface ReferenceDataScopeService {

    /**
     * The 5-digit FIPS code of a county.
     *
     * @return empty when the county has no FIPS code
     * @throws IllegalStateException if migration V42 has not been applied
     */
    Optional<String> countyFips(Long countyId);

    /**
     * The 2-digit FIPS code of a state, taken from its counties' FIPS codes.
     *
     * @param state state code (FL) or name (Florida)
     * @return empty when the state is unknown or none of its counties has a FIPS code
     * @throws IllegalStateException if migration V42 has not been applied, or the state's counties disagree
     */
    Optional<String> stateFips(String state);
}
