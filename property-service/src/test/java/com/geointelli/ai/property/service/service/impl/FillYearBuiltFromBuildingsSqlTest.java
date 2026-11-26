package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * The statement run after a buildings CSV import and migration V52 give every property still holding
 * "Multiple (See Building Info.)" the year of its first building (H2 in PostgreSQL mode).
 */
class FillYearBuiltFromBuildingsSqlTest {
    private static final String MULTIPLE = "Multiple (See Building Info.)";
    private static final LocalDateTime BEFORE = LocalDateTime.of(2020, 1, 1, 0, 0);
    private final int nextYear = Year.now().getValue() + 1;

    private JdbcDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:year-built-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE properties(id BIGINT PRIMARY KEY, year_built VARCHAR(255), updated_at TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE buildings(id BIGINT PRIMARY KEY, property_id BIGINT, actual INTEGER,"
            + " building_no INTEGER, seg_no INTEGER, roll_year INTEGER)");
        property(1, MULTIPLE);          // first building 1970, an older second building, an older roll year
        building(10, 1, 2025, 2, 1, 1960);
        building(11, 1, 2025, 1, 2, 1994);
        building(12, 1, 2024, 1, 1, 1950);
        building(13, 1, 2025, 1, 1, 1970);
        property(2, MULTIPLE);          // unusable first years: 9999 and next year
        building(20, 2, 2025, 1, 1, 9999);
        building(21, 2, 2025, 1, 2, nextYear);
        building(22, 2, 2025, 1, 3, 1985);
        property(3, MULTIPLE);          // no usable year
        building(30, 3, 2025, 1, 1, 9999);
        property(4, MULTIPLE);          // no building
        property(5, "1999");            // a year from the county stays
        building(50, 5, 2025, 1, 1, 1980);
        property(6, "MULTIPLE (SEE BUILDING INFO.)");
        building(60, 6, null, 1, 1, 1975);
        building(61, 6, 2025, 2, 1, 2003);
    }

    private void property(long id, String yearBuilt) {
        jdbc.update("INSERT INTO properties(id, year_built, updated_at) VALUES (?, ?, ?)", id, yearBuilt, BEFORE);
    }

    private void building(long id, long propertyId, Integer rollYear, int buildingNo, int segNo, int actual) {
        jdbc.update("INSERT INTO buildings(id, property_id, actual, building_no, seg_no, roll_year) VALUES (?, ?, ?, ?, ?, ?)",
            id, propertyId, actual, buildingNo, segNo, rollYear);
    }

    private String yearBuilt(long id) {
        return jdbc.queryForObject("SELECT year_built FROM properties WHERE id = ?", String.class, id);
    }

    private boolean touched(long id) {
        return jdbc.queryForObject("SELECT updated_at FROM properties WHERE id = ?", LocalDateTime.class, id).isAfter(BEFORE);
    }

    private void assertFilled() {
        assertThat(yearBuilt(1)).isEqualTo("1970");
        assertThat(yearBuilt(2)).isEqualTo("1985");
        assertThat(yearBuilt(3)).isEqualTo(MULTIPLE);
        assertThat(yearBuilt(4)).isEqualTo(MULTIPLE);
        assertThat(yearBuilt(5)).isEqualTo("1999");
        assertThat(yearBuilt(6)).isEqualTo("2003");
        assertThat(touched(1)).isTrue();
        assertThat(touched(3)).isFalse();
        assertThat(touched(5)).isFalse();
    }

    @Test
    @DisplayName("The import statement fills the first building's year, skips unusable years and runs again without change")
    void importStatement() {
        assertThat(jdbc.update(CsvImportServiceImpl.FILL_YEAR_BUILT_FROM_BUILDINGS)).isEqualTo(3);
        assertFilled();
        assertThat(jdbc.update(CsvImportServiceImpl.FILL_YEAR_BUILT_FROM_BUILDINGS)).isZero();
    }

    @Test
    @DisplayName("Migration V52 gives the same result as the import statement")
    void migration() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("db/migration/V52__fill_year_built_from_buildings.sql"));
            assertFilled();
            ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("db/migration/V52__fill_year_built_from_buildings.sql"));
        }
        assertFilled();
    }
}
