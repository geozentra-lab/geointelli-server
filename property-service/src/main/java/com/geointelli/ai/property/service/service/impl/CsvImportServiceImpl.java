package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.service.CountyService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.ParseException;
import com.geointelli.ai.property.service.entity.Owner;
import com.geointelli.ai.property.service.entity.Land;
import com.geointelli.ai.property.service.entity.Sale;
import com.geointelli.ai.property.service.entity.Tax;
import com.geointelli.ai.property.service.entity.ExtraFeature;
import com.geointelli.ai.property.service.entity.Parcel;
import com.geointelli.ai.property.service.entity.PropertyValuePrediction;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.geointelli.ai.property.service.entity.Address;
import com.geointelli.ai.property.service.entity.Assessment;
import com.geointelli.ai.property.service.entity.Building;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.PropertyImportMapping;
import com.geointelli.ai.property.service.repository.PropertyImportMappingRepository;
import com.geointelli.ai.property.service.service.CsvImportService;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@AllArgsConstructor 
public class CsvImportServiceImpl implements CsvImportService {
        private PropertyImportMappingRepository propertyImportMappingRepository;
        private EntityManager entityManager;
        private PlatformTransactionManager transactionManager;
        private CountyService countyService;

        @Override
        @Transactional(rollbackOn = IOException.class)
        public void importProperties(Path csvPath, String sourceCounty, String sourceState) throws IOException {
                importRecords(csvPath, record -> {
                        Long sourcePropertyId = Long.valueOf(record.get("id").trim());
                        countyService.ensureExists(sourceCounty, sourceState);
                        var existingMapping = propertyImportMappingRepository.findBySourceCountyAndSourceStateAndSourcePropertyId(
                                sourceCounty, sourceState, sourcePropertyId);
                        Property property = new Property();
                        assignCounty(property, sourceCounty, sourceState);
                        property.setBathroomCount(parseBigDecimal(record.get("bathroom_count")));
                        property.setBedroomCount(parseBigDecimal(record.get("bedroom_count")));
                        property.setBuildingActualArea(parseBigDecimal(record.get("building_actual_area")));
                        property.setBuildingBaseArea(parseBigDecimal(record.get("building_base_area")));
                        property.setBuildingEffectiveArea(parseBigDecimal(record.get("building_effective_area")));
                        property.setBuildingGrossArea(parseBigDecimal(record.get("building_gross_area")));
                        property.setBuildingHeatedArea(parseBigDecimal(record.get("building_heated_area")));
                        property.setDorCode(emptyToNull(record.get("dor_code")));
                        property.setDorDescription(emptyToNull(record.get("dor_description")));
                        property.setFloorCount(parseBigDecimal(record.get("floor_count")));
                        property.setFolio(emptyToNull(record.get("folio")));
                        property.setHalfBathroomCount(parseDouble(record.get("half_bathroom_count")));
                        property.setLotSize(parseDouble(record.get("lot_size")));
                        property.setMessage(emptyToNull(record.get("message")));
                        property.setMunicipality(emptyToNull(record.get("municipality")));
                        property.setNeighborhood(record.get("neighborhood"));
                        property.setNeighborhoodDescription(emptyToNull(record.get("neighborhood_description")));
                        if (record.isMapped("parent_folio")) {
                                property.setParentFolio(emptyToNull(record.get("parent_folio")));
                        }
                        property.setPrimaryZone(emptyToNull(record.get("primary_zone")));
                        property.setPrimaryZoneDescription(emptyToNull(record.get("primary_zone_description")));
                        property.setShowCurrentValuesFlag(emptyToNull(record.get("show_current_values_flag")));
                        property.setStatus(emptyToNull(record.get("status")));
                        property.setSubdivision(emptyToNull(record.get("subdivision")));
                        property.setUnitCount(parseInteger(record.get("unit_count")));
                        property.setYearBuilt(emptyToNull(record.get("year_built"))); 
                        log.info("inserting property with folio {}", property.getFolio());
                        entityManager.persist(property);
                        PropertyImportMapping mapping = existingMapping.orElseGet(PropertyImportMapping::new);
                        mapping.setSourceCounty(sourceCounty);
                        mapping.setSourceState(sourceState);
                        mapping.setSourcePropertyId(sourcePropertyId);
                        mapping.setGeozentraProperty(property);
                        // Existing mappings are managed; dirty checking saves their new target.
                        if (existingMapping.isEmpty()) {
                                entityManager.persist(mapping);
                        }
                });
                importParentRelationships(csvPath, sourceCounty, sourceState);
        }

        private void importParentRelationships(Path csvPath, String sourceCounty, String sourceState)
                        throws IOException {
                try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
                        CSVParser parser = CSVFormat.DEFAULT.builder().setHeader()
                                .setSkipHeaderRecord(true).get().parse(reader)) {
                        if (!parser.getHeaderMap().containsKey("parent_property_id")) {
                                return;
                        }
                }
                importRecords(csvPath, record -> {
                        Long parentSourceId = parseLong(record.get("parent_property_id"));
                        if (parentSourceId == null) {
                                return;
                        }
                        Long childSourceId = Long.valueOf(record.get("id").trim());
                        Property child = getMappedProperty(sourceCounty, sourceState, childSourceId);
                        PropertyImportMapping parentMapping = propertyImportMappingRepository
                                .findBySourceCountyAndSourceStateAndSourcePropertyId(
                                        sourceCounty, sourceState, parentSourceId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "No parent mapping for source property ID " + parentSourceId
                                        + " in " + sourceCounty + ", " + sourceState
                                        + " (child source ID " + childSourceId + ")"));
                        Property parent = parentMapping.getGeozentraProperty();
                        assignCounty(parent, sourceCounty, sourceState);
                        validateParentRelationship(child, parent);
                        child.setParentProperty(parent);
                });
        }

        private void validateParentRelationship(Property child, Property parent) {
                Set<Long> visitedIds = new HashSet<>();
                visitedIds.add(child.getId());
                Property ancestor = parent;
                while (ancestor != null) {
                        if (!visitedIds.add(ancestor.getId())) {
                                throw new IllegalArgumentException(
                                        "Parent relationship creates a cycle for property " + child.getId());
                        }
                        ancestor = ancestor.getParentProperty();
                }
        }

        @Override
        @Transactional(rollbackOn = IOException.class)
        public void importAddresses(Path csvPath, String sourceCounty, String sourceState) throws IOException {
                importRelatedRecords(csvPath, sourceCounty, sourceState, (record, property) -> {
                        Address address = new Address();
                        address.setProperty(property);
                        address.setAddress(emptyToNull(record.get("address")));
                        address.setBuildingNumber(parseInteger(record.get("building_number")));
                        address.setCity(emptyToNull(record.get("city")));
                        address.setHouseNumberSuffix(emptyToNull(record.get("house_number_suffix")));
                        address.setMessage(emptyToNull(record.get("message")));
                        address.setStreetName(emptyToNull(record.get("street_name")));
                        address.setStreetNumber(parseInteger(record.get("street_number")));
                        address.setStreetPrefix(emptyToNull(record.get("street_prefix")));
                        address.setStreetSuffix(emptyToNull(record.get("street_suffix")));
                        address.setStreetSuffixDirection(emptyToNull(record.get("street_suffix_direction")));
                        address.setUnit(emptyToNull(record.get("unit")));
                        address.setZip(emptyToNull(record.get("zip")));

                        entityManager.persist(address);
                });
        }

        @Override
        @Transactional(rollbackOn = IOException.class)
        public void importAssessments(Path csvPath, String sourceCounty, String sourceState) throws IOException {
                importRelatedRecords(csvPath, sourceCounty, sourceState, (record, property) -> {
                        Assessment assessment = new Assessment();
                        assessment.setProperty(property);
                        assessment.setAssessedValue(parseBigDecimal(record.get("assessed_value")));
                        assessment.setBuildingOnlyValue(parseBigDecimal(record.get("building_only_value")));
                        assessment.setExtraFeatureValue(parseBigDecimal(record.get("extra_feature_value")));
                        assessment.setLandValue(parseBigDecimal(record.get("land_value")));
                        assessment.setTotalValue(parseBigDecimal(record.get("total_value")));
                        assessment.setYear(parseInteger(record.get("year")));
                        assessment.setMessage(emptyToNull(record.get("message")));

                        entityManager.persist(assessment);
                });
        }

        @Override
        @Transactional(rollbackOn = IOException.class)
        public void importBuildings(Path csvPath, String sourceCounty, String sourceState) throws IOException {
                importRelatedRecords(csvPath, sourceCounty, sourceState, (record, property) -> {
                        Building building = new Building();
                        building.setProperty(property);
                        building.setActual(parseInteger(record.get("actual")));
                        building.setActualArea(parseInteger(record.get("actual_area")));
                        building.setAdjustedBasePrice(parseDouble(record.get("adjusted_base_price")));
                        building.setBuildingNo(parseInteger(record.get("building_no")));
                        building.setDepreciatedValue(parseBigDecimal(record.get("depreciated_value")));
                        building.setEffective(parseInteger(record.get("effective")));
                        building.setEffectiveArea(parseInteger(record.get("effective_area")));
                        building.setGrossArea(parseInteger(record.get("gross_area")));
                        building.setHeatedArea(parseBigDecimal(record.get("heated_area")));
                        building.setImprovementModelDesc(emptyToNull(record.get("improvement_model_desc")));
                        building.setMessage(emptyToNull(record.get("message")));
                        building.setPercentComp(parseDouble(record.get("percent_comp")));
                        building.setPercentageGood(parseDouble(record.get("percentage_good")));
                        building.setReplacementCostNew(parseBigDecimal(record.get("replacement_cost_new")));
                        building.setRollYear(parseInteger(record.get("roll_year")));
                        building.setSegNo(parseInteger(record.get("seg_no")));
                        building.setTotalAdjustedPoints(parseInteger(record.get("total_adjusted_points")));
                        building.setTraversePoints(emptyToNull(record.get("traverse_points")));

                        entityManager.persist(building);
                });
        }

        @Override
        @Transactional(rollbackOn = IOException.class)
        public void importOwners(Path ownersCsvPath, Path propertyOwnersCsvPath,
                        String sourceCounty, String sourceState) throws IOException {
                Map<Long, Long> ownerIds = new HashMap<>();
                importRecords(ownersCsvPath, record -> {
                        Long sourceOwnerId = Long.valueOf(record.get("id").trim());
                        if (ownerIds.containsKey(sourceOwnerId)) {
                                throw new IllegalArgumentException("Duplicate source owner ID: " + sourceOwnerId);
                        }
                        Owner entity = new Owner();
                        entity.setName(emptyToNull(record.get("name")));
                        entity.setDescription(emptyToNull(record.get("description")));
                        entity.setRole(emptyToNull(record.get("role")));
                        entity.setPercentageOwn(parseInteger(record.get("percentage_own")));
                        entity.setShortDescription(emptyToNull(record.get("short_description")));
                        entity.setTenancyCd(emptyToNull(record.get("tenancy_cd")));
                        entity.setMarriedFlag(parseBoolean(record.get("married_flag")));
                        entity.setMessage(emptyToNull(record.get("message")));
                        entityManager.persist(entity);
                        ownerIds.put(sourceOwnerId, entity.getId());
                });
                importRelatedRecords(propertyOwnersCsvPath, sourceCounty, sourceState, (record, property) -> {
                        Long sourceOwnerId = Long.valueOf(record.get("owner_id").trim());
                        Long ownerId = ownerIds.get(sourceOwnerId);
                        if (ownerId == null) {
                                throw new IllegalArgumentException("No owner in owners CSV for source owner ID: "
                                        + sourceOwnerId);
                        }
                        if (property.getOwners() == null) {
                                property.setOwners(new ArrayList<>());
                        }
                        boolean linked = property.getOwners().stream()
                                .anyMatch(existing -> existing.getId().equals(ownerId));
                        if (!linked) {
                                property.getOwners().add(entityManager.getReference(Owner.class, ownerId));
                        }
                });
        }

        @Override
        @Transactional(rollbackOn = IOException.class)
        public void importLands(Path csvPath, String sourceCounty, String sourceState) throws IOException {
                importRelatedRecords(csvPath, sourceCounty, sourceState, (record, property) -> {
                        Land entity = new Land();
                        entity.setAdjustedUnitPrice(parseDouble(record.get("adjusted_unit_price")));
                        entity.setCalculatedValue(parseBigDecimal(record.get("calculated_value")));
                        entity.setDepth(parseDouble(record.get("depth")));
                        entity.setFrontFeet(parseDouble(record.get("front_feet")));
                        entity.setLandUse(emptyToNull(record.get("land_use")));
                        entity.setLandlineType(emptyToNull(record.get("landline_type")));
                        entity.setMessage(emptyToNull(record.get("message")));
                        entity.setMuniZone(emptyToNull(record.get("muni_zone")));
                        entity.setMuniZoneDescription(emptyToNull(record.get("muni_zone_description")));
                        entity.setPaZoneDescription(emptyToNull(record.get("pa_zone_description")));
                        entity.setPercentCondition(parseDouble(record.get("percent_condition")));
                        entity.setRollYear(parseInteger(record.get("roll_year")));
                        entity.setTotalAdjustments(parseInteger(record.get("total_adjustments")));
                        entity.setUnitType(emptyToNull(record.get("unit_type")));
                        entity.setUnits(parseDouble(record.get("units")));
                        entity.setUseCode(emptyToNull(record.get("use_code")));
                        entity.setZone(emptyToNull(record.get("zone")));
                        entity.setProperty(property);
                        entityManager.persist(entity);
                });
        }

        @Override
        @Transactional(rollbackOn = IOException.class)
        public void importSales(Path csvPath, String sourceCounty, String sourceState) throws IOException {
                importRelatedRecords(csvPath, sourceCounty, sourceState, (record, property) -> {
                        Sale entity = new Sale();
                        entity.setSaleId(parseInteger(record.get("sale_id")));
                        entity.setSaleDate(parseLocalDate(record.get("sale_date")));
                        entity.setSalePrice(parseBigDecimal(record.get("sale_price")));
                        entity.setSaleType(emptyToNull(record.get("sale_type")));
                        entity.setInstrumentNumber(emptyToNull(record.get("instrument_number")));
                        entity.setOfficialRecordBook(emptyToNull(record.get("official_record_book")));
                        entity.setOfficialRecordPage(emptyToNull(record.get("official_record_page")));
                        entity.setEncodedRecordBookAndPage(emptyToNull(record.get("encoded_record_book_and_page")));
                        entity.setSaleInstrument(emptyToNull(record.get("sale_instrument")));
                        entity.setDocumentStamps(parseInteger(record.get("document_stamps")));
                        entity.setQualifiedFlag(emptyToNull(record.get("qualified_flag")));
                        entity.setQualificationDescription(emptyToNull(record.get("qualification_description")));
                        entity.setQualifiedSYear(parseInteger(record.get("qualifiedsyear")));
                        entity.setQualifiedSourceCode(emptyToNull(record.get("qualified_source_code")));
                        entity.setReasonCode(emptyToNull(record.get("reason_code")));
                        entity.setReviewCode(emptyToNull(record.get("review_code")));
                        entity.setGrantorName1(emptyToNull(record.get("grantor_name1")));
                        entity.setGrantorName2(emptyToNull(record.get("grantor_name2")));
                        entity.setGranteeName1(emptyToNull(record.get("grantee_name1")));
                        entity.setGranteeName2(emptyToNull(record.get("grantee_name2")));
                        entity.setVacantFlag(parseBoolean(record.get("vacant_flag")));
                        entity.setMessage(emptyToNull(record.get("message")));
                        entity.setProperty(property);
                        entityManager.persist(entity);
                });
        }

        @Override
        @Transactional(rollbackOn = IOException.class)
        public void importTaxes(Path csvPath, String sourceCounty, String sourceState) throws IOException {
                importRelatedRecords(csvPath, sourceCounty, sourceState, (record, property) -> {
                        Tax entity = new Tax();
                        entity.setCityExemptionValue(parseBigDecimal(record.get("city_exemption_value")));
                        entity.setCityTaxableValue(parseBigDecimal(record.get("city_taxable_value")));
                        entity.setCountyExemptionValue(parseBigDecimal(record.get("county_exemption_value")));
                        entity.setCountyTaxableValue(parseBigDecimal(record.get("county_taxable_value")));
                        entity.setRegionalExemptionValue(parseBigDecimal(record.get("regional_exemption_value")));
                        entity.setRegionalTaxableValue(parseBigDecimal(record.get("regional_taxable_value")));
                        entity.setSchoolExemptionValue(parseBigDecimal(record.get("school_exemption_value")));
                        entity.setSchoolTaxableValue(parseBigDecimal(record.get("school_taxable_value")));
                        entity.setYear(parseInteger(record.get("year")));
                        entity.setMessage(emptyToNull(record.get("message")));
                        entity.setProperty(property);
                        entityManager.persist(entity);
                });
        }

        @Override
        @Transactional(rollbackOn = IOException.class)
        public void importExtraFeatures(Path csvPath, String sourceCounty, String sourceState) throws IOException {
                importRelatedRecords(csvPath, sourceCounty, sourceState, (record, property) -> {
                        ExtraFeature entity = new ExtraFeature();
                        entity.setActualYearBuilt(parseInteger(record.get("actual_year_built")));
                        entity.setAdjustedUnitPrice(parseDouble(record.get("adjusted_unit_price")));
                        entity.setDepreciatedValue(parseBigDecimal(record.get("depreciated_value")));
                        entity.setDescription(emptyToNull(record.get("description")));
                        entity.setMessage(emptyToNull(record.get("message")));
                        entity.setPercentCondition(parseDouble(record.get("percent_condition")));
                        entity.setRollYear(parseInteger(record.get("roll_year")));
                        entity.setUnits(parseInteger(record.get("units")));
                        entity.setUseCode(emptyToNull(record.get("use_code")));
                        entity.setProperty(property);
                        entityManager.persist(entity);
                });
        }

        @Override
        @Transactional(rollbackOn = IOException.class)
        public void importParcels(Path csvPath, String sourceCounty, String sourceState) throws IOException {
                importRelatedRecords(csvPath, sourceCounty, sourceState, (record, property) -> {
                        Parcel entity = new Parcel();
                        entity.setId(parseLong(record.get("id")));
                        entity.setFolio(emptyToNull(record.get("folio")));
                        entity.setGeom(parseMultiPolygon(record.get("geom")));
                        entity.setLotSize(parseDouble(record.get("lot_size")));
                        entity.setProperty(property);
                        entityManager.persist(entity);
                });
        }

        @Override
        @Transactional(rollbackOn = IOException.class)
        public void importPropertyValuePredictions(Path csvPath, String sourceCounty, String sourceState) throws IOException {
                importRelatedRecords(csvPath, sourceCounty, sourceState, (record, property) -> {
                        PropertyValuePrediction entity = new PropertyValuePrediction();
                        entity.setPropertyId(property.getId());
                        entity.setPredictedPrice(parseBigDecimal(record.get("predicted_price")));
                        entity.setPredictedPriceLow(parseBigDecimal(record.get("predicted_price_low")));
                        entity.setPredictedPriceHigh(parseBigDecimal(record.get("predicted_price_high")));
                        entity.setLastSalePrice(parseBigDecimal(record.get("last_sale_price")));
                        entity.setLastSaleDate(parseLocalDate(record.get("last_sale_date")));
                        entity.setConfidenceScore(parseInteger(record.get("confidence_score")));
                        entity.setPredictionDate(parseLocalDate(record.get("prediction_date")));
                        entity.setModelVersion(emptyToNull(record.get("model_version")));
                        entityManager.persist(entity);
                });
        }

        private void importRelatedRecords(Path csvPath, String sourceCounty, String sourceState,
                        BiConsumer<CSVRecord, Property> importer) throws IOException {
                importRecords(csvPath, record -> {
                        Long sourcePropertyId = Long.valueOf(record.get("property_id").trim());
                        importer.accept(record, getMappedProperty(sourceCounty, sourceState, sourcePropertyId));
                });
        }

        private void importRecords(Path csvPath, Consumer<CSVRecord> importer) throws IOException {
                boolean wholeFileTransaction = TransactionSynchronizationManager.isActualTransactionActive();
                TransactionTemplate rowTransaction = new TransactionTemplate(transactionManager);
                try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
                        CSVParser parser = CSVFormat.DEFAULT.builder().setHeader()
                                .setSkipHeaderRecord(true).setIgnoreEmptyLines(true).get().parse(reader)) {
                        int imported = 0;
                        for (CSVRecord record : parser) {
                                try {
                                        if (wholeFileTransaction) {
                                                importer.accept(record);
                                        } else {
                                                // Commit each row when the caller has no whole-file transaction.
                                                rowTransaction.executeWithoutResult(status -> {
                                                        importer.accept(record);
                                                        entityManager.flush();
                                                        entityManager.clear();
                                                });
                                        }
                                } catch (IllegalArgumentException e) {
                                        throw new IllegalArgumentException("Invalid CSV record "
                                                + record.getRecordNumber() + " in " + csvPath + ": " + e.getMessage(), e);
                                }
                                imported++;
                                if (wholeFileTransaction && imported % 500 == 0) {
                                        entityManager.flush();
                                        entityManager.clear();
                                }
                        }
                        if (wholeFileTransaction) {
                                entityManager.flush();
                        }
                        log.info("CSV import completed for {}. Processed: {}", csvPath, imported);
                }
        }

        private Long parseLong(String value) {
                return (value == null || value.isBlank()) ? null : Long.valueOf(value.trim());
        }

        private LocalDate parseLocalDate(String value) {
                return (value == null || value.isBlank()) ? null : LocalDate.parse(value.trim());
        }

        private Boolean parseBoolean(String value) {
                if (value == null || value.isBlank()) {
                        return null;
                }
                return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
                        case "true", "t", "1", "yes", "y" -> true;
                        case "false", "f", "0", "no", "n" -> false;
                        default -> throw new IllegalArgumentException("Invalid boolean: " + value);
                };
        }

        private MultiPolygon parseMultiPolygon(String value) {
                if (value == null || value.isBlank()) {
                        return null;
                }
                try {
                        var geometry = new WKTReader().read(value.trim());
                        if (!(geometry instanceof MultiPolygon multiPolygon)) {
                                throw new IllegalArgumentException("Parcel geom must be a WKT MULTIPOLYGON");
                        }
                        multiPolygon.setSRID(4326);
                        return multiPolygon;
                } catch (ParseException e) {
                        throw new IllegalArgumentException("Invalid parcel WKT geometry", e);
                }
        }

        private String emptyToNull(String value) {
                return (value == null || value.isBlank()) ? null : value.trim();
        }

        private Integer parseInteger(String value) {
                return (value == null || value.isBlank()) ? null : Integer.valueOf(value.trim());
        }

        private Double parseDouble(String value) {
                return (value == null || value.isBlank()) ? null : Double.valueOf(value.trim());
        }

        private BigDecimal parseBigDecimal(String value) {
                return (value == null || value.isBlank()) ? null : new BigDecimal(value.trim());
        }


        private Property getMappedProperty(String sourceCounty, String sourceState, Long sourcePropertyId) {
                PropertyImportMapping mapping = propertyImportMappingRepository
                        .findBySourceCountyAndSourceStateAndSourcePropertyId(sourceCounty, sourceState,
                                sourcePropertyId).orElseThrow(() ->
                                new IllegalStateException("No property mapping found for source property ID: "
                                        + sourcePropertyId + " in " + sourceCounty + ", " + sourceState));

                Property property = mapping.getGeozentraProperty();
                assignCounty(property, sourceCounty, sourceState);
                return property;
        }

        private void assignCounty(Property property, String sourceCounty, String sourceState) {
                var county = countyService.resolve(sourceCounty, sourceState);
                if (property.getCounty() != null
                                && !county.getId().equals(property.getCounty().getId())) {
                        throw new IllegalArgumentException("Source mapping points to a property in another county");
                }
                property.setCounty(county);
        }
}
