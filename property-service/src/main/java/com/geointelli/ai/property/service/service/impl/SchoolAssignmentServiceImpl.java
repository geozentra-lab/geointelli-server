package com.geointelli.ai.property.service.service.impl;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.geointelli.ai.property.service.service.SchoolAssignmentService;

import lombok.extern.slf4j.Slf4j;

/**
 * Set-based PostGIS assignment of one county ({@link SchoolScoringSql}): the points of the county's parcels and the
 * candidate zones go into temporary tables, zones are matched to points through the points' GIST index, the levels
 * left over get the nearest zoned public school, and the result is compared with the stored rows so that only the
 * differences are written. A first assignment of Miami-Dade writes 1.7 million rows (the foreign key checks make it
 * the longest step); a repeated one with unchanged data writes none.
 */
@Service
@Slf4j
public class SchoolAssignmentServiceImpl implements SchoolAssignmentService {

    static final RowMapper<SchoolCounty> COUNTY = (rs, row) -> new SchoolCounty(rs.getLong("id"),
        rs.getString("fips_code"), rs.getString("name"), rs.getString("code"));

    private final JdbcTemplate jdbc;

    public SchoolAssignmentServiceImpl(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public List<SchoolCounty> countiesToAssign(String fips5) {
        return jdbc.query(SchoolScoringSql.COUNTIES_TO_ASSIGN, COUNTY, fips5, fips5);
    }

    @Override
    public boolean countyExists(String fips5) {
        return !jdbc.queryForList(SchoolScoringSql.COUNTY_ID, Long.class, fips5).isEmpty();
    }

    @Override
    public int countPropertiesToAssign(SchoolCounty county) {
        Integer count = jdbc.queryForObject(SchoolScoringSql.COUNT_TO_ASSIGN, Integer.class, county.countyId());
        return count == null ? 0 : count;
    }

    @Override
    public AssignmentResult assignCounty(String fips5) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A school assignment must run inside a transaction");
        }
        long started = System.nanoTime();
        jdbc.query(SchoolScoringSql.LOCK, rs -> null, LOCK_PREFIX + fips5);
        Long countyId = countyId(fips5);

        jdbc.execute(SchoolScoringSql.DROP_ASSIGNMENT_TABLES);
        jdbc.execute(SchoolScoringSql.CREATE_POINTS);
        jdbc.update(SchoolScoringSql.INSERT_POINTS, countyId);
        jdbc.execute(SchoolScoringSql.INDEX_POINTS);
        jdbc.execute("ANALYZE school_points");
        jdbc.execute(SchoolScoringSql.CREATE_ZONE_LEVELS);
        jdbc.update(SchoolScoringSql.INSERT_ZONE_LEVELS, fips5);
        jdbc.execute("ANALYZE school_zone_levels");
        jdbc.execute(SchoolScoringSql.CREATE_NEW_ASSIGNMENTS);
        int zoned = jdbc.update(SchoolScoringSql.INSERT_ZONE_MATCHES);
        jdbc.execute(SchoolScoringSql.CREATE_NEAREST_SCHOOLS);
        jdbc.update(SchoolScoringSql.INSERT_NEAREST_SCHOOLS, fips5);
        int nearest = jdbc.update(SchoolScoringSql.INSERT_NEAREST);
        // statistics for the comparison with the stored rows
        jdbc.execute("ANALYZE new_school_assignments");

        int deleted = jdbc.update(SchoolScoringSql.DELETE_VANISHED_ASSIGNMENTS, countyId);
        int updated = jdbc.update(SchoolScoringSql.UPDATE_CHANGED_ASSIGNMENTS);
        int inserted = jdbc.update(SchoolScoringSql.INSERT_NEW_ASSIGNMENTS);

        int properties = count(SchoolScoringSql.COUNT_POINTS);
        int assigned = count(SchoolScoringSql.COUNT_ASSIGNED_PROPERTIES);
        List<LevelCount> counts = jdbc.query(SchoolScoringSql.ASSIGNMENT_SUMMARY,
            (rs, row) -> new LevelCount(rs.getString("level"), rs.getString("method"), rs.getInt("n")));
        long millis = (System.nanoTime() - started) / 1_000_000;
        log.info("School assignment of {}: {} properties, {} assigned, {} zone rows, {} nearest rows; "
            + "{} inserted, {} updated, {} deleted in {} ms", fips5, properties, assigned, zoned, nearest, inserted,
            updated, deleted, millis);
        return new AssignmentResult(fips5, properties, assigned, counts, inserted, updated, deleted, millis);
    }

    private Long countyId(String fips5) {
        List<Long> ids = jdbc.queryForList(SchoolScoringSql.COUNTY_ID, Long.class, fips5);
        if (ids.isEmpty()) throw new IllegalArgumentException("No county has the FIPS code " + fips5);
        return ids.get(0);
    }

    private int count(String sql) {
        Integer count = jdbc.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }
}
