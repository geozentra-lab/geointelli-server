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
import com.geointelli.ai.property.service.entity.MarketEvidenceObservation;
import com.geointelli.ai.property.service.entity.MarketEvidenceType;
import com.geointelli.ai.property.service.entity.MarketGeographyLevel;
import com.geointelli.ai.property.service.entity.MarketObservationKind;
import com.geointelli.ai.property.service.entity.State;
import com.geointelli.ai.property.service.repository.CountyRepository;
import com.geointelli.ai.property.service.repository.MarketEvidenceObservationRepository;
import com.geointelli.ai.property.service.repository.StateRepository;
import com.geointelli.ai.property.service.service.MarketEvidenceImportService;
import lombok.RequiredArgsConstructor;

/**
 * Validates each market_evidence.csv row, links it to our state and county when they match, and upserts it
 * on the observation's identity (source, type, geography, period). Validation errors are
 * {@link IllegalArgumentException}s naming the column, which the CSV job worker records as INVALID_ROW with
 * the row number. The worker owns the transaction and commits in batches.
 */
@Service
@RequiredArgsConstructor
public class MarketEvidenceImportServiceImpl implements MarketEvidenceImportService {
    static final String SOURCE = "source";
    static final String EVIDENCE_TYPE = "evidence_type";
    static final String GEOGRAPHY_LEVEL = "geography_level";
    static final String GEOGRAPHY_CODE = "geography_code";
    static final String GEOGRAPHY_NAME = "geography_name";
    static final String STATE_CODE = "state_code";
    static final String COUNTY_NAME = "county_name";
    static final String PERIOD_START = "period_start";
    static final String PERIOD_END = "period_end";
    static final String SOURCE_DATASET = "source_dataset";
    static final String SOURCE_RELEASE_DATE = "source_release_date";
    static final String OBSERVATION_KIND = "observation_kind";
    static final String CURRENCY = "currency";
    static final String SALE_COUNT = "sale_count";
    static final String MEDIAN_SALE_PRICE = "median_sale_price";
    static final String AVERAGE_SALE_PRICE = "average_sale_price";
    static final String MEDIAN_SALE_PRICE_PER_SQFT = "median_sale_price_per_sqft";
    static final String ACTIVE_LISTING_COUNT = "active_listing_count";
    static final String NEW_LISTING_COUNT = "new_listing_count";
    static final String MEDIAN_ASKING_PRICE = "median_asking_price";
    static final String AVERAGE_ASKING_PRICE = "average_asking_price";
    static final String MEDIAN_ASKING_PRICE_PER_SQFT = "median_asking_price_per_sqft";
    static final String MEDIAN_DAYS_ON_MARKET = "median_days_on_market";
    static final String PRICE_REDUCED_COUNT = "price_reduced_count";

    private static final Pattern SOURCE_FORMAT = Pattern.compile("[A-Za-z0-9_.-]{2,64}");
    private static final Pattern STATE_FORMAT = Pattern.compile("[A-Za-z]{2}");
    private static final Pattern FIVE_DIGITS = Pattern.compile("\\d{5}");
    private static final Pattern CURRENCY_FORMAT = Pattern.compile("[A-Za-z]{3}");

    private final MarketEvidenceObservationRepository observations;
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
        MarketEvidenceType type = enumValue(record, EVIDENCE_TYPE, MarketEvidenceType.class, null);
        MarketGeographyLevel level = enumValue(record, GEOGRAPHY_LEVEL, MarketGeographyLevel.class, null);
        String code = geographyCode(level, required(record, GEOGRAPHY_CODE));
        LocalDate periodStart = date(record, PERIOD_START, true);
        LocalDate periodEnd = date(record, PERIOD_END, true);
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException(PERIOD_END + " must not be before " + PERIOD_START);
        }

        MarketEvidenceObservation row = observations
            .findObservation(source, type, level, code, periodStart, periodEnd)
            .orElseGet(MarketEvidenceObservation::new);
        row.setSource(source);
        row.setEvidenceType(type);
        row.setGeographyLevel(level);
        row.setGeographyCode(code);
        row.setPeriodStart(periodStart);
        row.setPeriodEnd(periodEnd);
        row.setGeographyName(optional(record, GEOGRAPHY_NAME));
        row.setSourceDataset(optional(record, SOURCE_DATASET));
        row.setSourceReleaseDate(date(record, SOURCE_RELEASE_DATE, false));
        row.setObservationKind(enumValue(record, OBSERVATION_KIND, MarketObservationKind.class, MarketObservationKind.ACTUAL));
        row.setCurrency(currency(record));
        linkGeography(row, record, stateCache, countyCache);
        figures(row, record, type);
        row.setImportedAt(Instant.now());
        row.setImportJobId(jobId);
        observations.save(row);
    }

    /** Sale figures on a SALE row, asking figures on an ASKING row; the other group must be empty. */
    private static void figures(MarketEvidenceObservation row, CSVRecord record, MarketEvidenceType type) {
        boolean sale = type == MarketEvidenceType.SALE;
        row.setSaleCount(count(record, SALE_COUNT, sale));
        row.setMedianSalePrice(amount(record, MEDIAN_SALE_PRICE, sale));
        row.setAverageSalePrice(amount(record, AVERAGE_SALE_PRICE, sale));
        row.setMedianSalePricePerSqft(amount(record, MEDIAN_SALE_PRICE_PER_SQFT, sale));
        row.setActiveListingCount(count(record, ACTIVE_LISTING_COUNT, !sale));
        row.setNewListingCount(count(record, NEW_LISTING_COUNT, !sale));
        row.setMedianAskingPrice(amount(record, MEDIAN_ASKING_PRICE, !sale));
        row.setAverageAskingPrice(amount(record, AVERAGE_ASKING_PRICE, !sale));
        row.setMedianAskingPricePerSqft(amount(record, MEDIAN_ASKING_PRICE_PER_SQFT, !sale));
        row.setMedianDaysOnMarket(amount(record, MEDIAN_DAYS_ON_MARKET, !sale));
        row.setPriceReducedCount(count(record, PRICE_REDUCED_COUNT, !sale));
        boolean any = sale
            ? row.getSaleCount() != null || row.getMedianSalePrice() != null || row.getAverageSalePrice() != null
                || row.getMedianSalePricePerSqft() != null
            : row.getActiveListingCount() != null || row.getNewListingCount() != null
                || row.getMedianAskingPrice() != null || row.getAverageAskingPrice() != null
                || row.getMedianAskingPricePerSqft() != null || row.getMedianDaysOnMarket() != null
                || row.getPriceReducedCount() != null;
        if (!any) {
            throw new IllegalArgumentException("a " + type + " row needs at least one "
                + type.name().toLowerCase(Locale.ROOT) + " figure");
        }
    }

    /**
     * STATE rows link to the state named by their code. COUNTY and ZIP rows link through the optional
     * {@code state_code} column and, for the county, {@code county_name} (or the geography name of a COUNTY
     * row). A state we do not know is a row error; a county we have not configured leaves the link empty.
     */
    private void linkGeography(MarketEvidenceObservation row, CSVRecord record,
            Map<String, Optional<State>> stateCache, Map<String, Optional<County>> countyCache) {
        String stateCode = row.getGeographyLevel() == MarketGeographyLevel.STATE
            ? row.getGeographyCode() : optional(record, STATE_CODE);
        State state = null;
        if (stateCode != null) {
            String key = stateCode.toUpperCase(Locale.ROOT);
            if (!STATE_FORMAT.matcher(key).matches()) {
                throw new IllegalArgumentException(STATE_CODE + ": use a two-letter state code, got '" + stateCode + "'");
            }
            state = stateCache.computeIfAbsent(key, states::findByCodeIgnoreCase)
                .orElseThrow(() -> new IllegalArgumentException("unknown state '" + key + "'"));
        }
        row.setState(state);

        String countyName = optional(record, COUNTY_NAME);
        if (countyName == null && row.getGeographyLevel() == MarketGeographyLevel.COUNTY) {
            countyName = row.getGeographyName();
        }
        County county = null;
        if (state != null && countyName != null) {
            String stateCode2 = state.getCode();
            String name = countyName;
            String key = stateCode2.toUpperCase(Locale.ROOT) + "/" + name.toLowerCase(Locale.ROOT);
            county = countyCache.computeIfAbsent(key, k -> counties.findByCountyAndState(name, stateCode2)).orElse(null);
        }
        row.setCounty(county);
    }

    private static String geographyCode(MarketGeographyLevel level, String code) {
        return switch (level) {
            case STATE -> {
                if (!STATE_FORMAT.matcher(code).matches()) {
                    throw new IllegalArgumentException(GEOGRAPHY_CODE
                        + ": a STATE row needs a two-letter state code, got '" + code + "'");
                }
                yield code.toUpperCase(Locale.ROOT);
            }
            case COUNTY -> {
                if (!FIVE_DIGITS.matcher(code).matches()) {
                    throw new IllegalArgumentException(GEOGRAPHY_CODE
                        + ": a COUNTY row needs a five-digit FIPS code, got '" + code + "'");
                }
                yield code;
            }
            case ZIP -> {
                if (!FIVE_DIGITS.matcher(code).matches()) {
                    throw new IllegalArgumentException(GEOGRAPHY_CODE
                        + ": a ZIP row needs a five-digit ZIP code, got '" + code + "'");
                }
                yield code;
            }
        };
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

    /** A whole, non-negative count; must be empty when the column belongs to the other evidence type. */
    private static Integer count(CSVRecord record, String column, boolean allowed) {
        String value = optional(record, column);
        if (value == null) return null;
        if (!allowed) throw new IllegalArgumentException(column + " must be empty on this evidence type");
        int parsed;
        try {
            parsed = Integer.parseInt(value.replace(",", ""));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(column + ": expected a whole number, got '" + value + "'");
        }
        if (parsed < 0) throw new IllegalArgumentException(column + " must not be negative");
        return parsed;
    }

    /** A non-negative amount; must be empty when the column belongs to the other evidence type. */
    private static BigDecimal amount(CSVRecord record, String column, boolean allowed) {
        String value = optional(record, column);
        if (value == null) return null;
        if (!allowed) throw new IllegalArgumentException(column + " must be empty on this evidence type");
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
