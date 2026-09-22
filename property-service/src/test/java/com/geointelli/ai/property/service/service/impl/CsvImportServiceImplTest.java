package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import com.geointelli.ai.property.service.entity.*;
import com.geointelli.ai.property.service.repository.*;
import jakarta.persistence.EntityManager;

class CsvImportServiceImplTest {
    @TempDir Path directory;
    private EntityManager entityManager;
    private PropertyImportMappingRepository mappings;
    private CsvImportServiceImpl service;
    private Property property;
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        mappings = mock(PropertyImportMappingRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> mock(TransactionStatus.class));
        service = new CsvImportServiceImpl(mappings, entityManager, transactionManager);
        property = new Property();
        property.setId(900L);
        PropertyImportMapping mapping = new PropertyImportMapping();
        mapping.setGeozentraProperty(property);
        when(mappings.findBySourceCountyAndSourceStateAndSourcePropertyId("county", "state", 12L))
                .thenReturn(Optional.of(mapping));
    }

    private Path csv(String contents) throws Exception {
        Path path = directory.resolve("input.csv");
        Files.writeString(path, contents);
        return path;
    }

    @Test
    void importsSalesUsingDatabaseCsvHeaders() throws Exception {
        Path path = csv("property_id,sale_id,sale_date,sale_price,sale_type,instrument_number,"
                + "official_record_book,official_record_page,encoded_record_book_and_page,sale_instrument,"
                + "document_stamps,qualified_flag,qualification_description,qualifiedsyear,qualified_source_code,"
                + "reason_code,review_code,grantor_name1,grantor_name2,grantee_name1,grantee_name2,vacant_flag,message\n"
                + "12,7,2025-01-02,125000.50, Residential , 000123 ,100,20,100/20,WD,100,Y,Qualified,2025,PA,"
                + "R,V,Seller,,Buyer,,false,Imported\n");

        service.importSales(path, "county", "state");

        ArgumentCaptor<Sale> captor = ArgumentCaptor.forClass(Sale.class);
        verify(entityManager).persist(captor.capture());
        Sale sale = captor.getValue();
        assertSame(property, sale.getProperty());
        assertEquals("Residential", sale.getSaleType());
        assertEquals("000123", sale.getInstrumentNumber());
        assertEquals(2025, sale.getQualifiedSYear());
        assertEquals(new java.math.BigDecimal("125000.50"), sale.getSalePrice());
    }

    @Test
    void commitsPreviousRowAndRollsBackFailingRow() throws Exception {
        Path path = csv("property_id,assessed_value,building_only_value,extra_feature_value,land_value,total_value,year,message\n"
                + "12,100,100,,0,100,2026,first\n"
                + "12,invalid,100,,0,100,2026,second\n");

        assertThrows(IllegalArgumentException.class,
                () -> service.importAssessments(path, "county", "state"));

        var order = inOrder(transactionManager, entityManager);
        order.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
        order.verify(entityManager).persist(any(Assessment.class));
        order.verify(entityManager).flush();
        order.verify(transactionManager).commit(any(TransactionStatus.class));
        order.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
        order.verify(transactionManager).rollback(any(TransactionStatus.class));
        verify(entityManager, times(1)).persist(any());
    }

    @Test
    void preservesExistingWholeFileTransaction() throws Exception {
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.importAssessments(csv("property_id,assessed_value,building_only_value,extra_feature_value,land_value,total_value,year,message\n"
                    + "12,100,100,,0,100,2026,first\n"
                    + "12,200,200,,0,200,2026,second\n"), "county", "state");

            verifyNoInteractions(transactionManager);
            verify(entityManager, times(2)).persist(any(Assessment.class));
            verify(entityManager).flush();
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void importsAddressUsingMappedProperty() throws Exception {
        Path path = csv("property_id,address,building_number,city,house_number_suffix,message,"
                + "street_name,street_number,street_prefix,street_suffix,street_suffix_direction,unit,zip\n"
                + "12, 123 Main St ,2, Baker ,A, , Main ,123,N,St,E, 4B ,00123\n");

        service.importAddresses(path, "county", "state");

        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(entityManager).persist(captor.capture());
        Address address = captor.getValue();
        assertSame(property, address.getProperty());
        assertNull(address.getId());
        assertEquals("123 Main St", address.getAddress());
        assertEquals(2, address.getBuildingNumber());
        assertEquals("Baker", address.getCity());
        assertEquals("A", address.getHouseNumberSuffix());
        assertNull(address.getMessage());
        assertEquals("Main", address.getStreetName());
        assertEquals(123, address.getStreetNumber());
        assertEquals("N", address.getStreetPrefix());
        assertEquals("St", address.getStreetSuffix());
        assertEquals("E", address.getStreetSuffixDirection());
        assertEquals("4B", address.getUnit());
        assertEquals("00123", address.getZip());
        verify(mappings).findBySourceCountyAndSourceStateAndSourcePropertyId("county", "state", 12L);
        verify(entityManager).flush();
    }

    @Test
    void importsPropertyAndSourceMapping() throws Exception {
        Path path = csv("id,bathroom_count,bedroom_count,building_actual_area,building_base_area,"
                + "building_effective_area,building_gross_area,building_heated_area,dor_code,dor_description,"
                + "floor_count,folio,half_bathroom_count,lot_size,message,municipality,neighborhood,"
                + "neighborhood_description,parent_folio,primary_zone,primary_zone_description,"
                + "show_current_values_flag,status,subdivision,unit_count,year_built\n"
                + " 42 ,2.5,3,1500,1400,1450,1600,1300,01, Residential ,2, 001234 ,1,7500.5,"
                + " ,Baker,N1,North neighborhood,,R1,Residential zone,Y,ACTIVE,Oak Park,1,2005\n");

        service.importProperties(path, "county", "state");

        ArgumentCaptor<Object> persisted = ArgumentCaptor.forClass(Object.class);
        verify(entityManager, times(2)).persist(persisted.capture());
        Property imported = assertInstanceOf(Property.class, persisted.getAllValues().get(0));
        assertNull(imported.getId());
        assertEquals("001234", imported.getFolio());
        assertEquals(new java.math.BigDecimal("2.5"), imported.getBathroomCount());
        assertEquals(new java.math.BigDecimal("3"), imported.getBedroomCount());
        assertEquals(new java.math.BigDecimal("1500"), imported.getBuildingActualArea());
        assertEquals(new java.math.BigDecimal("1400"), imported.getBuildingBaseArea());
        assertEquals(new java.math.BigDecimal("1450"), imported.getBuildingEffectiveArea());
        assertEquals(new java.math.BigDecimal("1600"), imported.getBuildingGrossArea());
        assertEquals(new java.math.BigDecimal("1300"), imported.getBuildingHeatedArea());
        assertEquals(new java.math.BigDecimal("2"), imported.getFloorCount());
        assertEquals(1.0, imported.getHalfBathroomCount());
        assertEquals(7500.5, imported.getLotSize());
        assertEquals("01", imported.getDorCode());
        assertEquals("Residential", imported.getDorDescription());
        assertNull(imported.getMessage());
        assertNull(imported.getParentFolio());
        assertEquals("Baker", imported.getMunicipality());
        assertEquals("N1", imported.getNeighborhood());
        assertEquals("North neighborhood", imported.getNeighborhoodDescription());
        assertEquals("R1", imported.getPrimaryZone());
        assertEquals("Residential zone", imported.getPrimaryZoneDescription());
        assertEquals("Y", imported.getShowCurrentValuesFlag());
        assertEquals("ACTIVE", imported.getStatus());
        assertEquals("Oak Park", imported.getSubdivision());
        assertEquals(1, imported.getUnitCount());
        assertEquals("2005", imported.getYearBuilt());

        PropertyImportMapping mapping = assertInstanceOf(PropertyImportMapping.class,
                persisted.getAllValues().get(1));
        assertEquals(42L, mapping.getSourcePropertyId());
        assertEquals("county", mapping.getSourceCounty());
        assertEquals("state", mapping.getSourceState());
        assertSame(imported, mapping.getGeozentraProperty());
        verify(mappings).findBySourceCountyAndSourceStateAndSourcePropertyId("county", "state", 42L);
        verify(entityManager).flush();
    }

    @Test
    void skipsAlreadyMappedProperty() throws Exception {
        service.importProperties(csv("id\n12\n"), "county", "state");
        verify(entityManager, never()).persist(any());
        verify(entityManager).flush();
    }

    @Test
    void importsAssessmentUsingMappedProperty() throws Exception {
        service.importAssessments(csv("property_id,assessed_value,building_only_value,extra_feature_value,land_value,total_value,year,message\n"
                + "12,123.45,100,,23.45,123.45,2026, imported \n"), "county", "state");
        ArgumentCaptor<Assessment> captor = ArgumentCaptor.forClass(Assessment.class);
        verify(entityManager).persist(captor.capture());
        assertSame(property, captor.getValue().getProperty());
        assertEquals(new java.math.BigDecimal("123.45"), captor.getValue().getAssessedValue());
        assertNull(captor.getValue().getExtraFeatureValue());
        assertEquals("imported", captor.getValue().getMessage());
        verify(entityManager).flush();
    }

    private Path ownersCsv() throws Exception {
        Path path = directory.resolve("owners.csv");
        Files.writeString(path, "id,name,description,role,percentage_own,short_description,tenancy_cd,married_flag,message\n"
                + "1, Jane Doe , ,OWNER,50,short,JT,Y,\n"
                + "2,John Doe,,OWNER,50,short,JT,N,\n");
        return path;
    }

    private void assignOwnerIds() {
        doAnswer(invocation -> {
            Object entity = invocation.getArgument(0);
            if (entity instanceof Owner owner) {
                owner.setId("Jane Doe".equals(owner.getName()) ? 800L : 801L);
            }
            return null;
        }).when(entityManager).persist(any());
        when(entityManager.getReference(eq(Owner.class), anyLong())).thenAnswer(invocation -> {
            Owner owner = new Owner();
            owner.setId(invocation.getArgument(1));
            return owner;
        });
    }

    @Test
    void importsOwnersAndManyToManyLinksUsingIntermediateCsv() throws Exception {
        assignOwnerIds();
        Property other = new Property();
        other.setId(901L);
        PropertyImportMapping otherMapping = new PropertyImportMapping();
        otherMapping.setGeozentraProperty(other);
        when(mappings.findBySourceCountyAndSourceStateAndSourcePropertyId("county", "state", 13L))
                .thenReturn(Optional.of(otherMapping));
        Path links = csv("property_id,owner_id,created_at,updated_at\n"
                + "12,1,2026-08-31 15:15:57,2026-08-31 15:15:57\n"
                + "12,2,,\n13,1,,\n12,1,,\n");
        service.importOwners(ownersCsv(), links, "county", "state");
        ArgumentCaptor<Owner> owners = ArgumentCaptor.forClass(Owner.class);
        verify(entityManager, times(2)).persist(owners.capture());
        assertEquals("Jane Doe", owners.getAllValues().get(0).getName());
        assertNull(owners.getAllValues().get(0).getDescription());
        assertTrue(owners.getAllValues().get(0).getMarriedFlag());
        assertEquals(java.util.List.of(800L, 801L), property.getOwners().stream().map(Owner::getId).toList());
        assertEquals(java.util.List.of(800L), other.getOwners().stream().map(Owner::getId).toList());
    }

    @Test
    void rejectsOwnerMissingFromOwnersCsv() throws Exception {
        assignOwnerIds();
        Path owners = ownersCsv();
        Path links = csv("property_id,owner_id\n12,99\n");
        assertThrows(IllegalArgumentException.class,
                () -> service.importOwners(owners, links, "county", "state"));
        assertNull(property.getOwners());
    }

    @Test
    void rejectsDuplicateSourceOwnerId() throws Exception {
        assignOwnerIds();
        Path owners = ownersCsv();
        Files.writeString(owners, "1,Duplicate,,OWNER,50,short,JT,Y,\n", java.nio.file.StandardOpenOption.APPEND);
        Path links = csv("property_id,owner_id\n12,1\n");
        assertThrows(IllegalArgumentException.class,
                () -> service.importOwners(owners, links, "county", "state"));
        assertNull(property.getOwners());
    }

    @Test
    void keepsOwnerIdMappingAcrossBatches() throws Exception {
        assignOwnerIds();
        Path owners = directory.resolve("owners.csv");
        StringBuilder content = new StringBuilder("id,name,description,role,percentage_own,short_description,tenancy_cd,married_flag,message\n");
        for (int id = 1; id <= 501; id++) {
            content.append(id).append(",Jane Doe,,OWNER,50,short,JT,Y,\n");
        }
        Files.writeString(owners, content);
        service.importOwners(owners, csv("property_id,owner_id\n12,1\n"), "county", "state");
        verify(entityManager, times(502)).clear();
        verify(entityManager).getReference(Owner.class, 800L);
        assertEquals(800L, property.getOwners().get(0).getId());
    }

    @Test
    void resolvesSourceIdForPrediction() throws Exception {
        service.importPropertyValuePredictions(csv("property_id,predicted_price,predicted_price_low,predicted_price_high,last_sale_price,last_sale_date,confidence_score,prediction_date,model_version\n"
                + "12,100.50,,120,90,2025-01-01,85,2026-01-01,v1\n"), "county", "state");
        ArgumentCaptor<PropertyValuePrediction> captor = ArgumentCaptor.forClass(PropertyValuePrediction.class);
        verify(entityManager).persist(captor.capture());
        assertEquals(900L, captor.getValue().getPropertyId());
        assertEquals(new java.math.BigDecimal("100.50"), captor.getValue().getPredictedPrice());
        assertNull(captor.getValue().getPredictedPriceLow());
    }

    @Test
    void importsParcelGeometryAndAssignedId() throws Exception {
        service.importParcels(csv("property_id,id,folio,geom,lot_size\n"
                + "12,44,F,\"MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0)))\",12.5\n"), "county", "state");
        ArgumentCaptor<Parcel> captor = ArgumentCaptor.forClass(Parcel.class);
        verify(entityManager).persist(captor.capture());
        assertEquals(44L, captor.getValue().getId());
        assertEquals(4326, captor.getValue().getGeom().getSRID());
        assertSame(property, captor.getValue().getProperty());
    }

    @Test
    void rejectsInvalidBooleanBeforePersisting() throws Exception {
        Path path = csv("id,name,description,role,percentage_own,short_description,tenancy_cd,married_flag,message\n"
                + "12,Jane,,OWNER,50,short,JT,invalid,\n");
        assertThrows(IllegalArgumentException.class,
                () -> service.importOwners(path, directory.resolve("links.csv"), "county", "state"));
        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsUnmappedProperty() throws Exception {
        when(mappings.findBySourceCountyAndSourceStateAndSourcePropertyId(anyString(), anyString(), anyLong()))
                .thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> service.importTaxes(csv("property_id\n12\n"), "county", "state"));
        verify(entityManager, never()).persist(any());
    }
}
