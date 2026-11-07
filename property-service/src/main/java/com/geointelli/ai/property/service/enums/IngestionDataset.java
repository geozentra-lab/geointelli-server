package com.geointelli.ai.property.service.enums;

import java.util.List;
import java.util.Optional;
import lombok.Getter;

/**
 * What one ingestion job loads. CSV datasets name the file they read and the columns the importer reads
 * unconditionally, so a file missing one of them is rejected before a job starts. API datasets name the
 * Miami-Dade ingestion they run.
 */
@Getter
public enum IngestionDataset {
    PROPERTIES(ImportJobType.CSV_IMPORT, "properties", "properties.csv", List.of(
        "id", "bathroom_count", "bedroom_count", "building_actual_area", "building_base_area",
        "building_effective_area", "building_gross_area", "building_heated_area", "dor_code", "dor_description",
        "floor_count", "folio", "half_bathroom_count", "lot_size", "message", "municipality", "neighborhood",
        "neighborhood_description", "primary_zone", "primary_zone_description", "show_current_values_flag",
        "status", "subdivision", "unit_count", "year_built")),
    ADDRESSES(ImportJobType.CSV_IMPORT, "addresses", "addresses.csv", List.of(
        "property_id", "address", "building_number", "city", "house_number_suffix", "message", "street_name",
        "street_number", "street_prefix", "street_suffix", "street_suffix_direction", "unit", "zip")),
    ASSESSMENTS(ImportJobType.CSV_IMPORT, "assessments", "assessments.csv", List.of(
        "property_id", "assessed_value", "building_only_value", "extra_feature_value", "land_value",
        "total_value", "year", "message")),
    BUILDINGS(ImportJobType.CSV_IMPORT, "buildings", "buildings.csv", List.of(
        "property_id", "actual", "actual_area", "adjusted_base_price", "building_no", "depreciated_value",
        "effective", "effective_area", "gross_area", "heated_area", "improvement_model_desc", "message",
        "percent_comp", "percentage_good", "replacement_cost_new", "roll_year", "seg_no",
        "total_adjusted_points", "traverse_points")),
    /** Reads owners.csv and property_owner.csv together; see {@link #PROPERTY_OWNER_HEADERS}. */
    OWNERS(ImportJobType.CSV_IMPORT, "owners", "owners.csv", List.of(
        "id", "name", "description", "role", "percentage_own", "short_description", "tenancy_cd",
        "married_flag", "message")),
    LANDS(ImportJobType.CSV_IMPORT, "lands", "lands.csv", List.of(
        "property_id", "adjusted_unit_price", "calculated_value", "depth", "front_feet", "land_use",
        "landline_type", "message", "muni_zone", "muni_zone_description", "pa_zone_description",
        "percent_condition", "roll_year", "total_adjustments", "unit_type", "units", "use_code", "zone")),
    SALES(ImportJobType.CSV_IMPORT, "sales", "sales.csv", List.of(
        "property_id", "sale_id", "sale_date", "sale_price", "sale_type", "instrument_number",
        "official_record_book", "official_record_page", "encoded_record_book_and_page", "sale_instrument",
        "document_stamps", "qualified_flag", "qualification_description", "qualifiedsyear",
        "qualified_source_code", "reason_code", "review_code", "grantor_name1", "grantor_name2",
        "grantee_name1", "grantee_name2", "vacant_flag", "message")),
    TAXES(ImportJobType.CSV_IMPORT, "taxes", "taxes.csv", List.of(
        "property_id", "city_exemption_value", "city_taxable_value", "county_exemption_value",
        "county_taxable_value", "regional_exemption_value", "regional_taxable_value",
        "school_exemption_value", "school_taxable_value", "year", "message")),
    EXTRA_FEATURES(ImportJobType.CSV_IMPORT, "extra-features", "extra_features.csv", List.of(
        "property_id", "actual_year_built", "adjusted_unit_price", "depreciated_value", "description",
        "message", "percent_condition", "roll_year", "units", "use_code")),
    PARCELS(ImportJobType.CSV_IMPORT, "parcels", "parcels.csv", List.of(
        "property_id", "id", "folio", "geom", "lot_size")),
    PROPERTY_VALUE_PREDICTIONS(ImportJobType.CSV_IMPORT, "property-value-predictions",
        "property_value_predictions.csv", List.of(
        "property_id", "predicted_price", "predicted_price_low", "predicted_price_high", "last_sale_price",
        "last_sale_date", "confidence_score", "prediction_date", "model_version")),
    PROPERTY_IMAGES(ImportJobType.CSV_IMPORT, "property-images", "property_images.csv", List.of(
        "property_id", "original_url", "primary_image", "display_order")),
    /** All county files in one job, in the order {@code CsvFolderImportService} uses. */
    FOLDER(ImportJobType.CSV_IMPORT, "folder", null, List.of()),

    API_PROPERTIES(ImportJobType.PROPERTY_INGESTION, "run", null, List.of()),
    API_BUILDINGS(ImportJobType.PROPERTY_INGESTION, "ingestbuildings", null, List.of()),
    API_EXTRA_FEATURES(ImportJobType.PROPERTY_INGESTION, "ingest_extrafeatures", null, List.of()),
    API_SALES(ImportJobType.PROPERTY_INGESTION, "ingest_sales", null, List.of()),
    API_ADDRESSES(ImportJobType.PROPERTY_INGESTION, "ingestaddresses", null, List.of());

    /** Columns read from property_owner.csv, the second file of the {@link #OWNERS} dataset. */
    public static final List<String> PROPERTY_OWNER_HEADERS = List.of("property_id", "owner_id");
    /** Second file of the {@link #OWNERS} dataset. */
    public static final String PROPERTY_OWNER_FILE = "property_owner.csv";

    /** Files a folder import requires, in import order. */
    public static final List<IngestionDataset> FOLDER_REQUIRED = List.of(PROPERTIES, ADDRESSES, ASSESSMENTS,
        BUILDINGS, OWNERS, LANDS, SALES, TAXES, EXTRA_FEATURES);
    /** Files a folder import loads only when present. */
    public static final List<IngestionDataset> FOLDER_OPTIONAL = List.of(PARCELS, PROPERTY_VALUE_PREDICTIONS,
        PROPERTY_IMAGES);
    /** Import order of a folder: properties, parcels, then the related files, predictions and images last. */
    public static final List<IngestionDataset> FOLDER_ORDER = List.of(PROPERTIES, PARCELS, ADDRESSES, ASSESSMENTS,
        BUILDINGS, OWNERS, LANDS, SALES, TAXES, EXTRA_FEATURES, PROPERTY_VALUE_PREDICTIONS, PROPERTY_IMAGES);

    private final ImportJobType jobType;
    /** Last path segment of the endpoint that starts this dataset. */
    private final String pathSegment;
    /** File name inside a county folder; null for folder and API datasets. */
    private final String fileName;
    /** Columns the importer reads unconditionally; checked against the CSV header before a job starts. */
    private final List<String> requiredHeaders;

    IngestionDataset(ImportJobType jobType, String pathSegment, String fileName, List<String> requiredHeaders) {
        this.jobType = jobType;
        this.pathSegment = pathSegment;
        this.fileName = fileName;
        this.requiredHeaders = requiredHeaders;
    }

    /** Whether this is one CSV file (or the owners pair) that a job reads row by row. */
    public boolean isSingleCsv() {
        return jobType == ImportJobType.CSV_IMPORT && this != FOLDER;
    }

    /** Resolves a single-file CSV dataset from its endpoint path segment ({@code extra-features} and so on). */
    public static Optional<IngestionDataset> csvFromPathSegment(String segment) {
        for (IngestionDataset dataset : values()) {
            if (dataset.isSingleCsv() && dataset.pathSegment.equals(segment)) return Optional.of(dataset);
        }
        return Optional.empty();
    }
}
