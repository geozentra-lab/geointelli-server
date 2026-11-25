package com.geointelli.ai.property.service.service;

import java.util.Optional;

/**
 * FIPS codes that scope a reference load, environmental (SCRUM-201) or school (SCRUM-202), read from
 * {@code counties.fips_code} (migration V42). A flood zone file belongs to one county; the statewide files to one state.
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

    /**
     * Whether a table of the public schema exists, so a load whose migration (V44 for the school tables) has not
     * been applied is refused before a job is queued.
     *
     * @param table table name, a constant of the loader
     */
    boolean hasTable(String table);
}
