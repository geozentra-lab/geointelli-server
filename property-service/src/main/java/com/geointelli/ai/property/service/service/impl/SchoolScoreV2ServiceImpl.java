package com.geointelli.ai.property.service.service.impl;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.postgresql.PGConnection;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.geointelli.ai.property.service.service.SchoolAssignmentService;
import com.geointelli.ai.property.service.service.SchoolAssignmentService.SchoolCounty;
import com.geointelli.ai.property.service.service.SchoolScoreV2Service;
import com.geointelli.ai.property.service.service.impl.SchoolChoiceIndex.Candidate;
import com.geointelli.ai.property.service.service.impl.SchoolScoreV2.Level;
import com.geointelli.ai.property.service.service.impl.SchoolScoreV2.Score;

import lombok.extern.slf4j.Slf4j;

/**
 * Scores one county: SQL gathers, per assigned property, its point and per level the assigned school, distance and
 * statewide percentile ({@link SchoolScoringSql#INSERT_SCORE_INPUTS}); Java computes the choice part
 * ({@link SchoolChoiceIndex}) and the score ({@link SchoolScoreV2}) page by page and streams the results into a
 * temporary table with COPY; SQL then writes only the rows that differ from the stored ones.
 */
@Service
@Slf4j
public class SchoolScoreV2ServiceImpl implements SchoolScoreV2Service {

    static final int PAGE_SIZE = 20_000;
    static final List<String> LEVELS = List.of("ELEMENTARY", "MIDDLE", "HIGH");

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public SchoolScoreV2ServiceImpl(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** Inputs of one assigned property; per level index 0 elementary, 1 middle, 2 high. */
    record Input(long propertyId, Double lat, Double lon, Long[] schoolIds, Double[] distances, Integer[] percentiles) { }

    static final RowMapper<Input> INPUT = (rs, row) -> {
        Long[] schools = new Long[3];
        Double[] distances = new Double[3];
        Integer[] percentiles = new Integer[3];
        for (int i = 0; i < 3; i++) {
            String prefix = LEVELS.get(i).toLowerCase();
            schools[i] = longOrNull(rs, prefix + "_school_id");
            distances[i] = doubleOrNull(rs, prefix + "_distance_m");
            percentiles[i] = intOrNull(rs, prefix + "_percentile");
        }
        return new Input(rs.getLong("property_id"), doubleOrNull(rs, "lat"), doubleOrNull(rs, "lon"), schools,
            distances, percentiles);
    };

    @Override
    public List<SchoolCounty> countiesToScore(String fips5) {
        return jdbc.query(SchoolScoringSql.COUNTIES_TO_SCORE, SchoolAssignmentServiceImpl.COUNTY, fips5, fips5);
    }

    @Override
    public int countPropertiesToScore(SchoolCounty county) {
        Integer count = jdbc.queryForObject(SchoolScoringSql.COUNT_TO_SCORE, Integer.class, county.countyId());
        return count == null ? 0 : count;
    }

    @Override
    public ScoreResult scoreCounty(String fips5) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A school score run must run inside a transaction");
        }
        long started = System.nanoTime();
        jdbc.query(SchoolScoringSql.LOCK, rs -> null, SchoolAssignmentService.LOCK_PREFIX + fips5);
        List<Long> ids = jdbc.queryForList(SchoolScoringSql.COUNTY_ID, Long.class, fips5);
        if (ids.isEmpty()) throw new IllegalArgumentException("No county has the FIPS code " + fips5);
        Long countyId = ids.get(0);
        Integer gradeYear = jdbc.queryForObject(SchoolScoringSql.GRADE_YEAR, Integer.class);
        SchoolChoiceIndex choices = new SchoolChoiceIndex(jdbc.query(SchoolScoringSql.CHOICE_SCHOOLS,
            (rs, row) -> new Candidate(rs.getString("level"), rs.getLong("school_id"), rs.getDouble("lat"),
                rs.getDouble("lon"), intOrNull(rs, "percentile")), gradeYear));

        jdbc.execute(SchoolScoringSql.DROP_SCORE_TABLES);
        jdbc.execute(SchoolScoringSql.CREATE_SCORE_INPUTS);
        int properties = jdbc.update(SchoolScoringSql.INSERT_SCORE_INPUTS, countyId, gradeYear, countyId);
        jdbc.execute("ANALYZE school_score_inputs");
        jdbc.execute(SchoolScoringSql.CREATE_NEW_SCORES);

        int scored = 0;
        long after = 0;
        List<Input> page;
        do {
            page = jdbc.query(SchoolScoringSql.SCORE_INPUT_PAGE, INPUT, after, PAGE_SIZE);
            StringBuilder csv = new StringBuilder(page.size() * 40);
            for (Input input : page) {
                Score score = score(input, choices);
                if (score.school() != null) scored++;
                csv.append(input.propertyId()).append(',').append(number(score.quality())).append(',')
                    .append(number(score.access())).append(',').append(number(score.choice())).append(',')
                    .append(number(score.school())).append(',').append(score.label() == null ? "" : score.label())
                    .append('\n');
            }
            if (!page.isEmpty()) {
                copy(csv.toString());
                after = page.get(page.size() - 1).propertyId();
            }
        } while (page.size() == PAGE_SIZE);
        jdbc.execute("ANALYZE new_school_scores");

        int deleted = jdbc.update(SchoolScoringSql.DELETE_VANISHED_SCORES, countyId);
        int updated = jdbc.update(SchoolScoringSql.UPDATE_CHANGED_SCORES, gradeYear, SchoolScoreV2.VERSION, gradeYear,
            SchoolScoreV2.VERSION);
        int inserted = jdbc.update(SchoolScoringSql.INSERT_NEW_SCORES, gradeYear, SchoolScoreV2.VERSION);
        long millis = (System.nanoTime() - started) / 1_000_000;
        log.info("School score v2 of {} (FLDOE {}): {} properties, {} scored; {} inserted, {} updated, {} deleted in {} ms",
            fips5, gradeYear, properties, scored, inserted, updated, deleted, millis);
        return new ScoreResult(fips5, gradeYear, properties, scored, inserted, updated, deleted, millis);
    }

    /** Score of one property: the choice part of each level excludes the school assigned at that level. */
    static Score score(Input input, SchoolChoiceIndex choices) {
        Level[] levels = new Level[3];
        for (int i = 0; i < 3; i++) {
            Double percentile = input.percentiles()[i] == null ? null : input.percentiles()[i].doubleValue();
            Double choice = choices.choice(LEVELS.get(i), input.lat(), input.lon(), input.schoolIds()[i]);
            levels[i] = new Level(percentile, input.distances()[i], choice);
        }
        return SchoolScoreV2.score(levels[0], levels[1], levels[2]);
    }

    /** COPY of one page of CSV lines into the temporary table, on the transaction's connection. */
    private void copy(String csv) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            connection.unwrap(PGConnection.class).getCopyAPI().copyIn(SchoolScoringSql.COPY_NEW_SCORES,
                new StringReader(csv));
        } catch (SQLException | IOException error) {
            throw new DataAccessResourceFailureException("COPY of the school scores failed: " + error.getMessage(), error);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static String number(Double value) {
        return value == null ? "" : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Double doubleOrNull(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
