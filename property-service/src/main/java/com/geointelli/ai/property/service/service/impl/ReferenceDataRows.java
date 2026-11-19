package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVRecord;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.enums.IngestionDataset;

/**
 * Turns one source row of an environmental reference file (SCRUM-201) into the values of its staging table.
 * Every value the source does not give becomes null (UNKNOWN): an empty cell, FEMA's -9999/-99999 "none", the NRI
 * ratings "No Rating", "Insufficient Data" and "Not Applicable" (and the 0 score FEMA puts next to "No Rating"),
 * an unrecognised NOAA time zone. Nothing is turned into a default or a low rating. A malformed value rejects the
 * row with a message naming the column.
 */
public final class ReferenceDataRows {

    /** NRI ratings that mean FEMA has no evidence; they are stored as null, never as low risk. */
    public static final Set<String> NRI_NO_EVIDENCE = Set.of("No Rating", "Insufficient Data", "Not Applicable");
    /** NRI hazards kept as columns: hurricane, inland and coastal flooding, tornado, wildfire, wind, lightning, heat. */
    public static final List<String> NRI_HAZARDS = List.of("HRCN", "IFLD", "CFLD", "TRND", "WFIR", "SWND", "LTNG", "HWAV");

    private static final Pattern FEMA_NONE = Pattern.compile("-9999(9)?(\\.0+)?");
    private static final Pattern NUMBER = Pattern.compile("[-+]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][-+]?\\d+)?");
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+");
    private static final Pattern DAMAGE = Pattern.compile("(\\d+(\\.\\d*)?|\\.\\d+)([KMBkmb])?");
    private static final Pattern TIME_ZONE = Pattern.compile("([A-Za-z]{2,4})(-?\\d{1,2})?");
    private static final Map<String, Integer> ZONE_HOURS = Map.ofEntries(
        Map.entry("EST", -5), Map.entry("EDT", -4), Map.entry("CST", -6), Map.entry("CDT", -5),
        Map.entry("MST", -7), Map.entry("MDT", -6), Map.entry("PST", -8), Map.entry("PDT", -7),
        Map.entry("AKST", -9), Map.entry("AKDT", -8), Map.entry("HST", -10), Map.entry("AST", -4),
        Map.entry("SST", -11), Map.entry("GST", 10), Map.entry("UTC", 0), Map.entry("GMT", 0));

    private ReferenceDataRows() {
    }

    /** One staging column: its name and PostgreSQL type. */
    public record Column(String name, String sqlType) { }

    /** Maps the rows of one file; {@code scope} is the county (flood) or state FIPS every row must belong to. */
    public interface RowMapper {
        /** Staging columns in output order; the first is always {@code source_row}. */
        List<Column> columns();

        /**
         * Normalised values for one data row, in {@link #columns()} order; null = SQL NULL.
         *
         * @throws IllegalArgumentException if a value is malformed or outside the scope
         */
        List<String> map(CSVRecord record);
    }

    /** The mapper of a reference dataset. */
    public static RowMapper forDataset(IngestionDataset dataset, String scope, List<String> header, ObjectMapper json) {
        return switch (dataset) {
            case FLOOD_ZONES -> new FloodZones(scope);
            case CENSUS_TRACTS -> new CensusTracts(scope);
            case NRI_TRACTS -> new NriTracts(scope, header, json);
            case STORM_EVENTS -> new StormEvents(scope);
            default -> throw new IllegalArgumentException("Not a reference dataset: " + dataset);
        };
    }

    private static List<Column> columnList(String... nameTypePairs) {
        List<Column> columns = new ArrayList<>();
        for (int i = 0; i < nameTypePairs.length; i += 2) columns.add(new Column(nameTypePairs[i], nameTypePairs[i + 1]));
        return List.copyOf(columns);
    }

    /** FEMA NFHL flood hazard zones of one county. */
    static final class FloodZones implements RowMapper {
        private static final List<Column> COLUMNS = columnList("source_row", "integer", "fips5", "text", "dfirm_id", "text",
            "fld_ar_id", "text", "fld_zone", "text", "zone_subty", "text", "sfha", "boolean", "static_bfe", "numeric",
            "depth", "numeric", "velocity", "numeric", "len_unit", "text", "vel_unit", "text", "v_datum", "text",
            "dual_zone", "text", "study_typ", "text", "version_id", "text", "source_cit", "text", "geom_wkt", "text");
        private final String countyFips;

        FloodZones(String countyFips) { this.countyFips = countyFips; }

        @Override public List<Column> columns() { return COLUMNS; }

        @Override
        public List<String> map(CSVRecord r) {
            String fips5 = text(r.get("fips5"));
            if (!countyFips.equals(fips5)) {
                throw new IllegalArgumentException("fips5 " + fips5 + " is not the county " + countyFips + " of this file");
            }
            return values(r.getRecordNumber(), fips5, text(r.get("DFIRM_ID")), text(r.get("FLD_AR_ID")),
                text(r.get("FLD_ZONE")), text(r.get("ZONE_SUBTY")), flag(r.get("SFHA_TF"), "SFHA_TF"),
                femaNumber(r.get("STATIC_BFE"), "STATIC_BFE"), femaNumber(r.get("DEPTH"), "DEPTH"),
                femaNumber(r.get("VELOCITY"), "VELOCITY"), text(r.get("LEN_UNIT")), text(r.get("VEL_UNIT")),
                text(r.get("V_DATUM")), text(r.get("DUAL_ZONE")), text(r.get("STUDY_TYP")), text(r.get("VERSION_ID")),
                text(r.get("SOURCE_CIT")), polygonWkt(r.get("geom_wkt"), false));
        }
    }

    /** Census TIGER/Line tracts of one state. */
    static final class CensusTracts implements RowMapper {
        private static final List<Column> COLUMNS = columnList("source_row", "integer", "tract_fips", "text", "fips5", "text",
            "name", "text", "land_area_m2", "bigint", "water_area_m2", "bigint", "geom_wkt", "text");
        private final String stateFips;

        CensusTracts(String stateFips) { this.stateFips = stateFips; }

        @Override public List<Column> columns() { return COLUMNS; }

        @Override
        public List<String> map(CSVRecord r) {
            String tract = tractFips(r.get("tract_fips"), stateFips, "tract_fips");
            String fips5 = text(r.get("fips5"));
            if (fips5 != null && !tract.startsWith(fips5)) {
                throw new IllegalArgumentException("fips5 " + fips5 + " does not match tract_fips " + tract);
            }
            return values(r.getRecordNumber(), tract, tract.substring(0, 5), text(r.get("name")),
                integer(r.get("land_area_m2"), "land_area_m2"), integer(r.get("water_area_m2"), "water_area_m2"),
                polygonWkt(r.get("geom_wkt"), true));
        }
    }

    /** FEMA National Risk Index per tract of one state; raw keeps every source column as text. */
    static final class NriTracts implements RowMapper {
        private static final List<Column> COLUMNS;

        static {
            List<String> pairs = new ArrayList<>(List.of("source_row", "integer", "tract_fips", "text", "fips5", "text",
                "nri_version", "text", "risk_score", "numeric", "risk_rating", "text"));
            for (String hazard : NRI_HAZARDS) {
                String prefix = hazard.toLowerCase(Locale.ROOT);
                pairs.addAll(List.of(prefix + "_score", "numeric", prefix + "_rating", "text"));
            }
            pairs.addAll(List.of("raw", "jsonb"));
            COLUMNS = columnList(pairs.toArray(String[]::new));
        }

        private final String stateFips;
        private final List<String> header;
        private final ObjectMapper json;

        NriTracts(String stateFips, List<String> header, ObjectMapper json) {
            this.stateFips = stateFips;
            this.header = header;
            this.json = json;
        }

        @Override public List<Column> columns() { return COLUMNS; }

        @Override
        public List<String> map(CSVRecord r) {
            String tract = tractFips(r.get("TRACTFIPS"), stateFips, "TRACTFIPS");
            List<String> values = new ArrayList<>(List.of(Long.toString(r.getRecordNumber()), tract, tract.substring(0, 5)));
            values.add(text(r.get("NRI_VER")));
            values.add(nriScore(r.get("RISK_SCORE"), r.get("RISK_RATNG"), "RISK_SCORE"));
            values.add(nriRating(r.get("RISK_RATNG")));
            for (String hazard : NRI_HAZARDS) {
                values.add(nriScore(r.get(hazard + "_RISKS"), r.get(hazard + "_RISKR"), hazard + "_RISKS"));
                values.add(nriRating(r.get(hazard + "_RISKR")));
            }
            Map<String, String> raw = new LinkedHashMap<>();
            for (String column : header) raw.put(column, r.get(column));
            try {
                values.add(json.writeValueAsString(raw));
            } catch (JsonProcessingException impossible) {
                throw new IllegalStateException(impossible);
            }
            return values;
        }
    }

    /** NOAA NCEI Storm Events of one state; county rows (C) and forecast/marine zone rows (Z/M). */
    static final class StormEvents implements RowMapper {
        private static final List<Column> COLUMNS = columnList("source_row", "integer", "event_id", "bigint",
            "episode_id", "bigint", "state_fips", "text", "fips5", "text", "cz_type", "text", "cz_fips", "integer",
            "cz_name", "text", "event_type", "text", "begin_at", "timestamptz", "end_at", "timestamptz",
            "cz_timezone", "text", "injuries_direct", "integer", "injuries_indirect", "integer",
            "deaths_direct", "integer", "deaths_indirect", "integer", "damage_property_usd", "numeric",
            "damage_crops_usd", "numeric", "magnitude", "numeric", "magnitude_type", "text", "tor_f_scale", "text",
            "flood_cause", "text", "begin_lat", "numeric", "begin_lon", "numeric");
        private final String stateFips;

        StormEvents(String stateFips) { this.stateFips = stateFips; }

        @Override public List<Column> columns() { return COLUMNS; }

        @Override
        public List<String> map(CSVRecord r) {
            String eventId = integer(r.get("EVENT_ID"), "EVENT_ID");
            if (eventId == null) throw new IllegalArgumentException("EVENT_ID is required");
            String state = text(r.get("STATE_FIPS"));
            if (state == null || !INTEGER.matcher(state).matches() || !pad(state, 2).equals(stateFips)) {
                throw new IllegalArgumentException("STATE_FIPS " + state + " is not the state " + stateFips + " of this import");
            }
            String czType = text(r.get("CZ_TYPE"));
            if (czType == null || !Set.of("C", "Z", "M").contains(czType)) {
                throw new IllegalArgumentException("CZ_TYPE must be C, Z or M, not " + czType);
            }
            String fips5 = text(r.get("fips5"));
            if (fips5 != null && (!fips5.matches("\\d{5}") || !fips5.startsWith(stateFips))) {
                throw new IllegalArgumentException("fips5 " + fips5 + " is not a county of the state " + stateFips);
            }
            String eventType = text(r.get("EVENT_TYPE"));
            if (eventType == null) throw new IllegalArgumentException("EVENT_TYPE is required");
            String zone = text(r.get("CZ_TIMEZONE"));
            String lat = coordinate(r.get("BEGIN_LAT"), 90, "BEGIN_LAT");
            String lon = coordinate(r.get("BEGIN_LON"), 180, "BEGIN_LON");
            if (lat == null || lon == null) {
                lat = null;
                lon = null;
            }
            return values(r.getRecordNumber(), eventId, integer(r.get("EPISODE_ID"), "EPISODE_ID"), stateFips, fips5,
                czType, integer(r.get("CZ_FIPS"), "CZ_FIPS"), text(r.get("CZ_NAME")), eventType,
                noaaTime(r.get("BEGIN_YEARMONTH"), r.get("BEGIN_DAY"), r.get("BEGIN_TIME"), zone, "BEGIN"),
                noaaTime(r.get("END_YEARMONTH"), r.get("END_DAY"), r.get("END_TIME"), zone, "END"), zone,
                integer(r.get("INJURIES_DIRECT"), "INJURIES_DIRECT"), integer(r.get("INJURIES_INDIRECT"), "INJURIES_INDIRECT"),
                integer(r.get("DEATHS_DIRECT"), "DEATHS_DIRECT"), integer(r.get("DEATHS_INDIRECT"), "DEATHS_INDIRECT"),
                damage(r.get("DAMAGE_PROPERTY"), "DAMAGE_PROPERTY"), damage(r.get("DAMAGE_CROPS"), "DAMAGE_CROPS"),
                number(r.get("MAGNITUDE"), "MAGNITUDE"), text(r.get("MAGNITUDE_TYPE")), text(r.get("TOR_F_SCALE")),
                text(r.get("FLOOD_CAUSE")), lat, lon);
        }
    }

    private static List<String> values(long row, String... values) {
        List<String> list = new ArrayList<>(values.length + 1);
        list.add(Long.toString(row));
        for (String value : values) list.add(value);
        return list;
    }

    /** Trimmed text; empty means unknown (null). */
    static String text(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** A FEMA number; empty, -9999 and -99999 mean "none" and become null. */
    static String femaNumber(String value, String column) {
        String text = text(value);
        if (text == null || FEMA_NONE.matcher(text).matches()) return null;
        return number(text, column);
    }

    static String number(String value, String column) {
        String text = text(value);
        if (text == null) return null;
        if (!NUMBER.matcher(text).matches()) throw new IllegalArgumentException(column + " is not a number: " + text);
        return new BigDecimal(text).toPlainString();
    }

    static String integer(String value, String column) {
        String text = text(value);
        if (text == null) return null;
        if (!INTEGER.matcher(text).matches()) throw new IllegalArgumentException(column + " is not an integer: " + text);
        return Long.toString(Long.parseLong(text));
    }

    /** FEMA's T/F flag; empty is unknown. */
    static String flag(String value, String column) {
        String text = text(value);
        if (text == null) return null;
        return switch (text.toUpperCase(Locale.ROOT)) {
            case "T" -> "true";
            case "F" -> "false";
            default -> throw new IllegalArgumentException(column + " must be T or F, not " + text);
        };
    }

    /**
     * Polygon WKT, checked only for its shape (PostGIS parses and repairs it). Empty is allowed for flood zones,
     * whose row is then kept without geometry, and refused for tracts.
     */
    static String polygonWkt(String value, boolean required) {
        String text = text(value);
        if (text == null) {
            if (required) throw new IllegalArgumentException("geom_wkt is required");
            return null;
        }
        String head = text.substring(0, Math.min(text.length(), 16)).toUpperCase(Locale.ROOT);
        if (!(head.startsWith("POLYGON") || head.startsWith("MULTIPOLYGON")) || !text.endsWith(")")) {
            throw new IllegalArgumentException("geom_wkt must be a WKT POLYGON or MULTIPOLYGON");
        }
        return text;
    }

    /** 11-digit tract FIPS; a numeric value that lost its leading zero is padded. */
    static String tractFips(String value, String stateFips, String column) {
        String text = text(value);
        if (text == null || !text.matches("\\d{1,11}")) throw new IllegalArgumentException(column + " must be an 11-digit tract FIPS, not " + text);
        String tract = pad(text, 11);
        if (!tract.startsWith(stateFips)) {
            throw new IllegalArgumentException(column + " " + tract + " is not in the state " + stateFips + " of this import");
        }
        return tract;
    }

    /** An NRI rating; "No Rating", "Insufficient Data", "Not Applicable" and empty are no evidence (null). */
    static String nriRating(String value) {
        String text = text(value);
        return text == null || NRI_NO_EVIDENCE.contains(text) ? null : text;
    }

    /**
     * An NRI score, null when its rating says there is no evidence: FEMA writes 0 next to "No Rating", and 0 would
     * read as the lowest risk. A score whose rating is merely empty is kept.
     */
    static String nriScore(String score, String rating, String column) {
        String label = text(rating);
        if (label != null && NRI_NO_EVIDENCE.contains(label)) return null;
        return number(score, column);
    }

    /**
     * NOAA damage text in US dollars: "25.00K" = 25000, "1.5M" = 1500000, "2B"; "0" is a recorded zero; empty is
     * null, and so is a unit without an amount ("K", once in the Florida file): the amount was not given.
     */
    static String damage(String value, String column) {
        String text = text(value);
        if (text == null || text.matches("[KMBkmb]")) return null;
        Matcher matcher = DAMAGE.matcher(text);
        if (!matcher.matches()) throw new IllegalArgumentException(column + " is not a NOAA damage amount: " + text);
        BigDecimal amount = new BigDecimal(matcher.group(1));
        String unit = matcher.group(3);
        if (unit != null) {
            amount = amount.multiply(switch (unit.toUpperCase(Locale.ROOT)) {
                case "K" -> BigDecimal.valueOf(1_000);
                case "M" -> BigDecimal.valueOf(1_000_000);
                default -> BigDecimal.valueOf(1_000_000_000);
            });
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    /**
     * A NOAA time from yyyyMM, day and HHmm (BEGIN_DATE_TIME has two-digit years, so it is not used) with the
     * offset of CZ_TIMEZONE ("EST-5", "CST", ...). An unrecognised time zone gives null (unknown) instead of a
     * guessed offset.
     */
    static String noaaTime(String yearMonth, String day, String hhmm, String zone, String column) {
        String ym = text(yearMonth);
        String d = text(day);
        String t = text(hhmm);
        if (ym == null && d == null && t == null) return null;
        if (ym == null || !ym.matches("\\d{6}") || d == null || !d.matches("\\d{1,2}") || t == null || !t.matches("\\d{1,4}")) {
            throw new IllegalArgumentException(column + " date/time is incomplete: " + ym + " " + d + " " + t);
        }
        ZoneOffset offset = offset(zone);
        if (offset == null) return null;
        int time = Integer.parseInt(t);
        int hour = time / 100;
        int minute = time % 100;
        try {
            LocalDateTime local = LocalDateTime.of(Integer.parseInt(ym.substring(0, 4)), Integer.parseInt(ym.substring(4)),
                Integer.parseInt(d), 0, 0);
            if (hour == 24 && minute == 0) local = local.plusDays(1);
            else local = local.withHour(hour).withMinute(minute);
            return OffsetDateTime.of(local, offset).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (java.time.DateTimeException invalid) {
            throw new IllegalArgumentException(column + " is not a valid date/time: " + ym + " " + d + " " + t);
        }
    }

    /** UTC offset of a NOAA CZ_TIMEZONE value; "EST-5" uses the given hours, "EST" the zone's standard offset. */
    static ZoneOffset offset(String zone) {
        String text = text(zone);
        if (text == null) return null;
        Matcher matcher = TIME_ZONE.matcher(text);
        if (!matcher.matches()) return null;
        if (matcher.group(2) != null) {
            int hours = Integer.parseInt(matcher.group(2));
            return hours < -18 || hours > 18 ? null : ZoneOffset.ofHours(hours);
        }
        Integer hours = ZONE_HOURS.get(matcher.group(1).toUpperCase(Locale.ROOT));
        return hours == null ? null : ZoneOffset.ofHours(hours);
    }

    private static String coordinate(String value, int limit, String column) {
        String text = number(value, column);
        if (text == null) return null;
        if (new BigDecimal(text).abs().compareTo(BigDecimal.valueOf(limit)) > 0) {
            throw new IllegalArgumentException(column + " is out of range: " + text);
        }
        return text;
    }

    private static String pad(String digits, int length) {
        return digits.length() >= length ? digits : "0".repeat(length - digits.length()) + digits;
    }
}
