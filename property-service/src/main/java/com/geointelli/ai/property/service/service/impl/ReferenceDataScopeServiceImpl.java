package com.geointelli.ai.property.service.service.impl;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.service.ReferenceDataScopeService;

/** Reads county and state FIPS codes with plain SQL, so the County entity does not depend on migration V42. */
@Service
public class ReferenceDataScopeServiceImpl implements ReferenceDataScopeService {
    private static final String MIGRATION_MISSING = "counties.fips_code is missing: apply migration V42 first";

    private final JdbcTemplate jdbc;

    public ReferenceDataScopeServiceImpl(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public Optional<String> countyFips(Long countyId) {
        if (countyId == null) return Optional.empty();
        try {
            List<String> codes = jdbc.queryForList("SELECT fips_code FROM counties WHERE id = ? AND fips_code IS NOT NULL",
                String.class, countyId);
            return codes.stream().findFirst();
        } catch (BadSqlGrammarException missingColumn) {
            throw new IllegalStateException(MIGRATION_MISSING, missingColumn);
        }
    }

    @Override
    public Optional<String> stateFips(String state) {
        if (state == null || state.isBlank()) return Optional.empty();
        List<String> prefixes;
        try {
            prefixes = jdbc.queryForList("""
                SELECT DISTINCT left(c.fips_code, 2)
                FROM counties c JOIN states s ON s.id = c.state_id
                WHERE c.fips_code IS NOT NULL AND (upper(s.code) = upper(?) OR lower(s.name) = lower(?))""",
                String.class, state.trim(), state.trim());
        } catch (BadSqlGrammarException missingColumn) {
            throw new IllegalStateException(MIGRATION_MISSING, missingColumn);
        }
        if (prefixes.size() > 1) {
            throw new IllegalStateException("The counties of " + state + " have FIPS codes of several states: " + prefixes);
        }
        return prefixes.stream().findFirst();
    }

    @Override
    public boolean hasTable(String table) {
        Boolean exists = jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, "public." + table);
        return Boolean.TRUE.equals(exists);
    }
}
