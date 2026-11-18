package com.geointelli.ai.property.service.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


/**
 * JdbcTemplate implementation of {@link PriceDivergenceReader}. The group conditions use the same expressions as the
 * comparable search (V32 indexes on parent_property_id and on county_id with LOWER(BTRIM(...))).
 */
@Repository
@RequiredArgsConstructor
public class JdbcPriceDivergenceReader implements PriceDivergenceReader{

    private static final String SUBJECT = """
            SELECT p.id, p.folio, p.county_id, p.parent_property_id,
                   NULLIF(NULLIF(BTRIM(p.subdivision), ''), '0') AS community,
                   NULLIF(NULLIF(BTRIM(p.neighborhood), ''), '0') AS neighborhood,
                   p.building_heated_area
            FROM properties p
            WHERE p.id = ?
            """;

    private static final String GROUP_FROM = """
            FROM properties p
            JOIN property_value_predictions v ON v.property_id = p.id
            WHERE %s
              AND p.id <> ?
              AND v.predicted_price > 0
              AND v.valuation_status IN ('supported', 'limited')
            """;

    private static final String GROUP_VALUATIONS = """
            SELECT COUNT(*) AS sample_size,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY v.predicted_price) AS median_value,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY v.predicted_price / p.building_heated_area)
                       FILTER (WHERE p.building_heated_area > 0) AS median_price_per_sqft,
                   MIN(v.prediction_date) AS valuations_from,
                   MAX(v.prediction_date) AS valuations_to
            """ + GROUP_FROM;

    private static final String GROUP_FACTORS = "SELECT v.valuation_factors " + GROUP_FROM
            + "  AND v.valuation_factors IS NOT NULL\n";

    private static final String BUILDING = "p.parent_property_id = ?";
    private static final String COMMUNITY = "p.county_id = ? AND LOWER(BTRIM(p.subdivision)) = LOWER(?)";
    private static final String MICRO_MARKET = "p.county_id = ? AND LOWER(BTRIM(p.neighborhood)) = LOWER(?)";

    private final JdbcTemplate jdbc;

    /** {@inheritDoc} */
    @Override
    public Optional<Subject> findSubject(Long propertyId) {
        List<Subject> rows = jdbc.query(SUBJECT, (rs, row) -> new Subject(
                rs.getLong("id"),
                rs.getString("folio"),
                rs.getObject("county_id", Long.class),
                rs.getObject("parent_property_id", Long.class),
                rs.getString("community"),
                rs.getString("neighborhood"),
                rs.getBigDecimal("building_heated_area")), propertyId);
        return rows.stream().findFirst();
    }

    /** {@inheritDoc} */
    @Override
    public GroupValuations groupValuations(Level level, Subject subject) {
        return jdbc.queryForObject(GROUP_VALUATIONS.formatted(condition(level)), (rs, row) -> new GroupValuations(
                rs.getLong("sample_size"),
                rs.getBigDecimal("median_value"),
                rs.getBigDecimal("median_price_per_sqft"),
                rs.getObject("valuations_from", LocalDate.class),
                rs.getObject("valuations_to", LocalDate.class)), parameters(level, subject));
    }

    /** {@inheritDoc} */
    @Override
    public List<String> groupValuationFactors(Level level, Subject subject) {
        return jdbc.queryForList(GROUP_FACTORS.formatted(condition(level)), String.class, parameters(level, subject));
    }

    /** Fixed SQL per level; no user text is ever concatenated. */
    private static String condition(Level level) {
        return switch (level) {
            case BUILDING -> BUILDING;
            case COMMUNITY -> COMMUNITY;
            case MICRO_MARKET -> MICRO_MARKET;
        };
    }

    /** Group key parameters followed by the excluded subject ID. */
    private static Object[] parameters(Level level, Subject subject) {
        return switch (level) {
            case BUILDING -> new Object[]{subject.parentPropertyId(), subject.id()};
            case COMMUNITY -> new Object[]{subject.countyId(), subject.community(), subject.id()};
            case MICRO_MARKET -> new Object[]{subject.countyId(), subject.neighborhood(), subject.id()};
        };
    }
}
