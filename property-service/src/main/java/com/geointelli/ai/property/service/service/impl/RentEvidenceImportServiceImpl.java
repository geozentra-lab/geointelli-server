package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.RentEvidenceObservation;
import com.geointelli.ai.property.service.entity.RentEvidenceType;
import com.geointelli.ai.property.service.entity.RentGeographyLevel;
import com.geointelli.ai.property.service.entity.RentObservationKind;
import com.geointelli.ai.property.service.entity.RentPeriod;
import com.geointelli.ai.property.service.entity.State;
import com.geointelli.ai.property.service.repository.CountyRepository;
import com.geointelli.ai.property.service.repository.RentEvidenceObservationRepository;
import com.geointelli.ai.property.service.repository.StateRepository;
import com.geointelli.ai.property.service.service.RentEvidenceImportService;
import lombok.RequiredArgsConstructor;

/**
 * Validates each rent_evidence.csv row, links it to our state and county when they match, and upserts it on
 * the observation's identity (source, type, geography, period, rent period, bedrooms). Every amount is stored
 * in the row's own rent period, as published; nothing is converted. Validation errors are
 * {@link IllegalArgumentException}s naming the column, which the CSV job worker records as INVALID_ROW with
 * the row number. The worker owns the transaction and commits in batches.
 */
@Service
@RequiredArgsConstructor
public class RentEvidenceImportServiceImpl implements RentEvidenceImportService {
    static final String SOURCE = "source";
    static final String EVIDENCE_TYPE = "evidence_type";
    static final String GEOGRAPHY_LEVEL = "geography_level";
    static final String GEOGRAPHY_CODE = "geography_code";
    static final String GEOGRAPHY_NAME = "geography_name";
    static final String STATE_CODE = "state_code";
    static final String COUNTY_NAME = "county_name";
    static final String PERIOD_START = "period_start";
    static final String PERIOD_END = "period_end";
    static final String RENT_PERIOD = "rent_period";
    static final String SOURCE_DATASET = "source_dataset";
    static final String SOURCE_RELEASE_DATE = "source_release_date";
    static final String OBSERVATION_KIND = "observation_kind";
    static final String CURRENCY = "currency";
    static final String BEDROOMS = "bedrooms";
    static final String MEDIAN_RENT = "median_rent";
    static final String AVERAGE_RENT = "average_rent";
    static final String MEDIAN_RENT_PER_SQFT = "median_rent_per_sqft";
    static final String MEDIAN_RENT_MOE = "median_rent_moe";
    static final String SAMPLE_COUNT = "sample_count";

    /** Bedroom counts above this are treated as a typing error rather than a real market segment. */
    private static final int MAX_BEDROOMS = 20;

    private static final Pattern SOURCE_FORMAT = Pattern.compile("[A-Za-z0-9_.-]{2,64}");
    private static final Pattern STATE_FORMAT = Pattern.compile("[A-Za-z]{2}");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern CURRENCY_FORMAT = Pattern.compile("[A-Za-z]{3}");

    private final RentEvidenceObservationRepository observations;
    private final StateRepository states;
    private final CountyRepository counties;

    @Override
    public Consumer<CSVRecord> rowImporter(UUID jobId) {
        String job = jobId == null ? null : jobId.toString();
        Map<String, Optional<State>> stateCache = new HashMap<>();
        Map<String, Optional<County>> countyCache = new HashMap<>();
        return record -> importRow(record, job, stateCache, countyCache);
    }

    private void importRow(CSVRecord record, String jobId, Map<String, Optional<State>> stateCache,
            Map<String, Optional<County>> countyCache) {
        String source = required(record, SOURCE);
        if (!SOURCE_FORMAT.matcher(source).matches()) {
            throw new IllegalArgumentException(SOURCE + ": use 2-64 letters, digits, underscore, dot or dash, got '" + source + "'");
        }
        source = source.toUpperCase(Locale.ROOT);
        RentEvidenceType type = enumValue(record, EVIDENCE_TYPE, RentEvidenceType.class, null);
        RentGeographyLevel level = enumValue(record, GEOGRAPHY_LEVEL, RentGeographyLevel.class, null);
        String code = geographyCode(level, required(record, GEOGRAPHY_CODE));
        LocalDate periodStart = date(record, PERIOD_START, true);
        LocalDate periodEnd = date(record, PERIOD_END, true);
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException(PERIOD_END + " must not be before " + PERIOD_START);
        }
        // Required on every row: the unit of all its amounts. A file may hold MONTHLY and ANNUAL rows, but each
        // row is one unit and the two are kept as separate observations, never added or converted here.
        RentPeriod rentPeriod = enumValue(record, RENT_PERIOD, RentPeriod.class, null);
        Short bedrooms = bedrooms(record);

        RentEvidenceObservation row = observations
            .findObservation(source, type, level, code, periodStart, periodEnd, rentPeriod, bedrooms)
            .orElseGet(RentEvidenceObservation::new);
        row.setSource(source);
        row.setEvidenceType(type);
        row.setGeographyLevel(level);
        row.setGeographyCode(code);
        row.setPeriodStart(periodStart);
        row.setPeriodEnd(periodEnd);
        row.setRentPeriod(rentPeriod);
        row.setBedrooms(bedrooms);
        row.setGeographyName(optional(record, GEOGRAPHY_NAME));
        row.setSourceDataset(optional(record, SOURCE_DATASET));
        row.setSourceReleaseDate(date(record, SOURCE_RELEASE_DATE, false));
        row.setObservationKind(enumValue(record, OBSERVATION_KIND, RentObservationKind.class, RentObservationKind.ACTUAL));
        row.setCurrency(currency(record));
        linkGeography(row, record, stateCache, countyCache);
        figures(row, record);
        row.setImportedAt(Instant.now());
        row.setImportJobId(jobId);
        observations.save(row);
    }

    /** The amounts, all in the row's rent period; at least one of median, average or per-sqft rent is needed. */
    private static void figures(RentEvidenceObservation row, CSVRecord record) {
        row.setMedianRent(amount(record, MEDIAN_RENT));
        row.setAverageRent(amount(record, AVERAGE_RENT));
        row.setMedianRentPerSqft(amount(record, MEDIAN_RENT_PER_SQFT));
        row.setMedianRentMoe(amount(record, MEDIAN_RENT_MOE));
        row.setSampleCount(count(record, SAMPLE_COUNT));
        if (row.getMedianRent() == null && row.getAverageRent() == null && row.getMedianRentPerSqft() == null) {
            throw new IllegalArgumentException("a row needs at least one of " + MEDIAN_RENT + ", " + AVERAGE_RENT
                + " or " + MEDIAN_RENT_PER_SQFT);
        }
        if (row.getMedianRentMoe() != null && row.getMedianRent() == null) {
            throw new IllegalArgumentException(MEDIAN_RENT_MOE + " needs a " + MEDIAN_RENT + " on the same row");
        }
    }

    /**
     * Links the row to our state and county when they match. STATE rows name their state in the code. COUNTY
     * and TRACT codes start with the five-digit county FIPS code, which finds the county and its state. ZIP
     * rows use the optional {@code state_code} and {@code county_name} columns. A state code we do not know, or
     * one that contradicts the county found by FIPS, is a row error; a county we have not configured leaves the
     * link empty.
     */
    private void linkGeography(RentEvidenceObservation row, CSVRecord record,
            Map<String, Optional<State>> stateCache, Map<String, Optional<County>> countyCache) {
        RentGeographyLevel level = row.getGeographyLevel();
        String stateCode = level == RentGeographyLevel.STATE ? row.getGeographyCode() : optional(record, STATE_CODE);
        State state = null;
        if (stateCode != null) {
            String key = stateCode.toUpperCase(Locale.ROOT);
            if (!STATE_FORMAT.matcher(key).matches()) {
                throw new IllegalArgumentException(STATE_CODE + ": use a two-letter state code, got '" + stateCode + "'");
            }
            state = stateCache.computeIfAbsent(key, states::findByCodeIgnoreCase)
                .orElseThrow(() -> new IllegalArgumentException("unknown state '" + key + "'"));
        }

        County county = null;
        if (level == RentGeographyLevel.COUNTY || level == RentGeographyLevel.TRACT) {
            String fips = row.getGeographyCode().substring(0, 5);
            county = countyCache.computeIfAbsent("fips/" + fips, k -> counties.findByFipsCode(fips)).orElse(null);
            if (county != null) {
                State countyState = county.getState();
                if (state != null && !state.getId().equals(countyState.getId())) {
                    throw new IllegalArgumentException(STATE_CODE + " '" + state.getCode() + "' does not match county FIPS "
                        + fips + " (" + county.getName() + ", " + countyState.getCode() + ")");
                }
                state = countyState;
            }
        } else if (level == RentGeographyLevel.ZIP && state != null) {
            String countyName = optional(record, COUNTY_NAME);
            if (countyName != null) {
                String stateKey = state.getCode();
                String key = stateKey.toUpperCase(Locale.ROOT) + "/" + countyName.toLowerCase(Locale.ROOT);
                county = countyCache.computeIfAbsent(key, k -> counties.findByCountyAndState(countyName, stateKey))
                    .orElse(null);
            }
        }
        row.setState(state);
        row.setCounty(county);
    }

    /** Checks the code against the level: two letters for a state, digits of the exact length otherwise. */
    private static String geographyCode(RentGeographyLevel level, String code) {
        if (level == RentGeographyLevel.STATE) {
            if (!STATE_FORMAT.matcher(code).matches()) {
                throw new IllegalArgumentException(GEOGRAPHY_CODE
                    + ": a STATE row needs a two-letter state code, got '" + code + "'");
            }
            return code.toUpperCase(Locale.ROOT);
        }
        if (!DIGITS.matcher(code).matches() || code.length() != level.codeLength()) {
            throw new IllegalArgumentException(GEOGRAPHY_CODE + ": a " + level + " row needs a " + level.codeLength()
                + "-digit code, got '" + code + "'");
        }
        return code;
    }

    /** Empty means all sizes together; otherwise a whole number from 0 (studio) to {@value #MAX_BEDROOMS}. */
    private static Short bedrooms(CSVRecord record) {
        Integer value = count(record, BEDROOMS);
        if (value == null) return null;
        if (value > MAX_BEDROOMS) {
            throw new IllegalArgumentException(BEDROOMS + " must be between 0 and " + MAX_BEDROOMS + ", got " + value);
        }
        return value.shortValue();
    }

    private static String currency(CSVRecord record) {
        String value = optional(record, CURRENCY);
        if (value == null) return "USD";
        if (!CURRENCY_FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(CURRENCY + ": use a three-letter code, got '" + value + "'");
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private static String required(CSVRecord record, String column) {
        String value = optional(record, column);
        if (value == null) throw new IllegalArgumentException(column + " is required");
        return value;
    }

    /** Trimmed value of a column, or null when the column is absent from the file or the cell is blank. */
    private static String optional(CSVRecord record, String column) {
        if (!record.isMapped(column)) return null;
        String value = record.get(column);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <E extends Enum<E>> E enumValue(CSVRecord record, String column, Class<E> type, E fallback) {
        String value = optional(record, column);
        if (value == null) {
            if (fallback != null) return fallback;
            throw new IllegalArgumentException(column + " is required");
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(column + ": expected one of " + Arrays.toString(type.getEnumConstants())
                + ", got '" + value + "'");
        }
    }

    private static LocalDate date(CSVRecord record, String column, boolean mandatory) {
        String value = mandatory ? required(record, column) : optional(record, column);
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException(column + ": expected a date like 2026-01-31, got '" + value + "'");
        }
    }

    /** A whole, non-negative number, or null when the cell is empty. */
    private static Integer count(CSVRecord record, String column) {
        String value = optional(record, column);
        if (value == null) return null;
        int parsed;
        try {
            parsed = Integer.parseInt(value.replace(",", ""));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(column + ": expected a whole number, got '" + value + "'");
        }
        if (parsed < 0) throw new IllegalArgumentException(column + " must not be negative");
        return parsed;
    }

    /** A non-negative amount, or null when the cell is empty. */
    private static BigDecimal amount(CSVRecord record, String column) {
        String value = optional(record, column);
        if (value == null) return null;
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(value.replace(",", "").replace("$", ""));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(column + ": expected a number, got '" + value + "'");
        }
        if (parsed.signum() < 0) throw new IllegalArgumentException(column + " must not be negative");
        return parsed;
    }
}
