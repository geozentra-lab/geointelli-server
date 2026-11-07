package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.CountyService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.reactive.function.client.WebClientException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.client.MiamiDadeApiClient;
import com.geointelli.ai.property.service.client.dto.PropertyApiResponse;
import com.geointelli.ai.property.service.client.dto.SiteAddress;
import com.geointelli.ai.property.service.entity.Address;
import com.geointelli.ai.property.service.entity.Assessment;
import com.geointelli.ai.property.service.entity.Building;
import com.geointelli.ai.property.service.entity.ExtraFeature;
import com.geointelli.ai.property.service.entity.Land;
import com.geointelli.ai.property.service.entity.Owner;
import com.geointelli.ai.property.service.entity.Parcel;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.Sale;
import com.geointelli.ai.property.service.entity.Tax;
import com.geointelli.ai.property.service.mapper.AddressMapper;
import com.geointelli.ai.property.service.mapper.AssessmentMapper;
import com.geointelli.ai.property.service.mapper.BuildingMapper;
import com.geointelli.ai.property.service.mapper.ExtraFeatureMapper;
import com.geointelli.ai.property.service.mapper.LandMapper;
import com.geointelli.ai.property.service.mapper.OwnerMapper;
import com.geointelli.ai.property.service.mapper.PropertyMapper;
import com.geointelli.ai.property.service.mapper.SaleMapper;
import com.geointelli.ai.property.service.mapper.TaxMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalAddressMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalAssessmentMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalBuildingMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalExtraFeatureMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalLandMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalOwnerMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalPropertyMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalSaleMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalTaxMapper;
import com.geointelli.ai.property.service.repository.OwnerRepository;
import com.geointelli.ai.property.service.repository.ParcelRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.PropertyIngestionService;
import com.geointelli.ai.property.service.service.PropertyService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps Miami-Dade API responses onto entities. The original {@code ingest*} methods fetch inside their
 * transaction and swallow failures; {@link #ingestForJob} fetches outside and reports outcomes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyIngestionServiceImpl implements PropertyIngestionService {
    /** Body the API client returns instead of a payload on 4xx responses. */
    static final String CLIENT_ERROR_BODY = "Client Error response";
    static final String PROPERTY_EXISTS = "PROPERTY_EXISTS";
    static final String PROPERTY_NOT_FOUND = "PROPERTY_NOT_FOUND";
    static final String ALREADY_PRESENT = "ALREADY_PRESENT";

    private final MiamiDadeApiClient miameDadaApiClient;
    private final ObjectMapper objectMapper;
    private final ParcelRepository parcelRepository;
    private final OwnerRepository ownerRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyMapper propertyMapper;
    private final ExternalPropertyMapper externalPropertyMapper;
    private final OwnerMapper ownerMapper;
    private final ExternalOwnerMapper externalOwnerMapper;
    private final AssessmentMapper assessmentMapper;
    private final ExternalAssessmentMapper externalAssessmentMapper;
    private final SaleMapper saleMapper;
    private final ExternalSaleMapper externalSaleMapper;
    private final LandMapper landMapper;
    private final ExternalLandMapper externalLandMapper;
    private final TaxMapper taxMapper;
    private final ExternalTaxMapper externalTaxMapper;
    private final BuildingMapper buildingMapper;
    private final ExternalBuildingMapper externalBuildingMapper;
    private final AddressMapper addressMapper;
    private final ExternalAddressMapper externalAddressMapper;
    private final ExtraFeatureMapper extraFeatureMapper;
    private final ExternalExtraFeatureMapper externalExtraFeatureMapper;
    private final PropertyService propertyService;
    private final CountyService countyService;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Transactional
    public void ingest(String folio) {
        try {
            Property existingProperty = propertyRepository.findByFolioAndCounty_Id(folio, countyService.miamiDade().getId()).orElse(null);
            if(existingProperty != null){
                log.info("property already found with this folio");
                return;
            }

            PropertyApiResponse api = parse(miameDadaApiClient.importMiameDadePropertyDetails(folio).block());
            createProperty(folio, api);
            log.info("---------------------Ingested folio {}------------------", folio);

        } catch (Exception e) {
            log.error("Failed ingest folio {}", folio, e);
        }
    }

    @Override
    public FolioResult ingestForJob(IngestionDataset dataset, String folio) {
        Long countyId = countyService.miamiDade().getId();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        String skipReason = transaction.execute(status -> skipReason(dataset, folio, countyId));
        if (skipReason != null) return FolioResult.skipped(skipReason);

        // The county API is called outside any transaction; connections are only held while writing.
        PropertyApiResponse api = fetch(folio);
        return transaction.execute(status -> apply(dataset, folio, countyId, api));
    }

    /** Why nothing needs to be written for the folio, or null when it should be loaded. */
    private String skipReason(IngestionDataset dataset, String folio, Long countyId) {
        Property property = propertyRepository.findByFolioAndCounty_Id(folio, countyId).orElse(null);
        if (dataset == IngestionDataset.API_PROPERTIES) {
            return property == null ? null : PROPERTY_EXISTS;
        }
        if (property == null) return PROPERTY_NOT_FOUND;
        return switch (dataset) {
            case API_ADDRESSES -> property.getAddress() == null ? null : ALREADY_PRESENT;
            case API_BUILDINGS -> property.getBuildings() == null ? null : ALREADY_PRESENT;
            case API_EXTRA_FEATURES -> property.getExtraFeatures() == null || property.getExtraFeatures().isEmpty()
                ? null : ALREADY_PRESENT;
            case API_SALES -> property.getSales() == null || property.getSales().isEmpty() ? null : ALREADY_PRESENT;
            default -> throw new IllegalArgumentException("Not an API dataset: " + dataset);
        };
    }

    /** Writes the fetched data; re-checks the database so a concurrent job cannot duplicate it. */
    private FolioResult apply(IngestionDataset dataset, String folio, Long countyId, PropertyApiResponse api) {
        String skipReason = skipReason(dataset, folio, countyId);
        if (skipReason != null) return FolioResult.skipped(skipReason);
        if (dataset == IngestionDataset.API_PROPERTIES) {
            createProperty(folio, api);
            return FolioResult.written();
        }
        Property property = propertyRepository.findByFolioAndCounty_Id(folio, countyId).orElseThrow();
        switch (dataset) {
            case API_ADDRESSES -> applyAddress(property, api);
            case API_BUILDINGS -> applyBuildings(property, api);
            case API_EXTRA_FEATURES -> applyExtraFeatures(property, api);
            case API_SALES -> applySales(property, api);
            default -> throw new IllegalArgumentException("Not an API dataset: " + dataset);
        }
        propertyRepository.save(property);
        return FolioResult.written();
    }

    private PropertyApiResponse fetch(String folio) {
        String response;
        try {
            response = miameDadaApiClient.importMiameDadePropertyDetails(folio).block();
        } catch (WebClientException | IllegalStateException error) {
            throw new SourceApiException("SOURCE_API_UNAVAILABLE", "The county API did not answer", error);
        }
        if (response == null || CLIENT_ERROR_BODY.equals(response)) {
            throw new SourceApiException("SOURCE_API_CLIENT_ERROR", "The county API rejected the folio request", null);
        }
        try {
            return parse(response);
        } catch (Exception error) {
            throw new SourceApiException("SOURCE_API_RESPONSE_INVALID", "The county API response could not be read", error);
        }
    }

    private PropertyApiResponse parse(String response) throws Exception {
        return objectMapper.readValue(response, PropertyApiResponse.class);
    }

    /** Creates the property with its related entities and attaches its parcels, as {@link #ingest} always did. */
    private void createProperty(String folio, PropertyApiResponse api) {
        Property property = mapProperty(api);
        property.setCounty(countyService.miamiDade());
        linkEntities(property, api);

        log.info("-------------------saving the property-----------------------");
        Property savedProperty = propertyService.saveProperty(property);

        attachParcel(folio, savedProperty);
    }

    private Property mapProperty(PropertyApiResponse api) {
        return propertyMapper.toEntity(
                externalPropertyMapper.toDTO(api.getPropertyInfo())
        );
    }

    private String sanitize(String s) {
        if (s == null) return null;
        return s.replace("\u0000", "").trim();
    }

    private void linkEntities(Property property, PropertyApiResponse api) {

        List<Owner> owners = api.getOwnerInfos().stream()
        .map(externalOwnerMapper::toDTO)
        .map(ownerMapper::toEntity)
        .map(o -> {
            o.setName(sanitize(o.getName()));
            o.setTenancyCd(sanitize(o.getTenancyCd()));
            o.setRole(sanitize(o.getRole()));
            o.setDescription(sanitize(o.getDescription()));
            o.setShortDescription(sanitize(o.getShortDescription()));
            o.setMessage(sanitize(o.getMessage()));

            if (o.getId() == null) {
                List<Owner> existingList =
                ownerRepository.findAllByNameAndTenancyCdAndRole(
                    sanitize(o.getName()),
                    sanitize(o.getTenancyCd()),
                    sanitize(o.getRole())
                );

                if (!existingList.isEmpty()) {
                    return existingList.get(0);
                }
                return ownerRepository.save(o);
            }
            return o;
        })
        .collect(Collectors.toList());

        owners.forEach(o -> {
            if (o.getProperties() == null) o.setProperties(new ArrayList<>());
            o.getProperties().add(property);
        });

        List<Assessment> assessments = api.getAssessment().getAssessmentInfos().stream()
            .map(externalAssessmentMapper::toDTO)
            .map(assessmentMapper::toEntity)
            .peek(a -> a.setProperty(property))
            .collect(Collectors.toList());

        List<Sale> sales = mapSales(property, api);

        List<Land> lands = api.getLand().getLandlines().stream()
                .map(externalLandMapper::toDTO)
                .map(landMapper::toEntity)
                .peek(l -> l.setProperty(property))
                .collect(Collectors.toList());

        List<Tax> taxes = api.getTaxable().getTaxableInfos().stream()
                .map(externalTaxMapper::toDTO)
                .map(taxMapper::toEntity)
                .peek(t -> t.setProperty(property))
                .collect(Collectors.toList());

        List<Building> buildings = mapBuildings(property, api);

        List<SiteAddress> addresses = api.getSiteAddress();
        if(!addresses.isEmpty()){
            Address address = addressMapper.toEntity(externalAddressMapper.toDTO(addresses.get(0)));
            property.setAddress(address);
        }

        property.setOwners(owners);
        if(property.getAssessments() == null)
            property.setAssessments(new ArrayList<>());
        property.getAssessments().addAll(assessments);

        if(property.getSales() == null)
            property.setSales(new ArrayList<>());
        property.getSales().addAll(sales);

        if(property.getLands() == null)
            property.setLands(new ArrayList<>());
        property.getLands().addAll(lands);

        if(property.getTaxes() == null)
            property.setTaxes(new ArrayList<>());
        property.getTaxes().addAll(taxes);

        if(property.getBuildings() == null)
            property.setBuildings(new ArrayList<>());
        property.getBuildings().addAll(buildings);
    }

    private List<Sale> mapSales(Property property, PropertyApiResponse api) {
        return api.getSalesInfos().stream()
                .map(externalSaleMapper::toDTO)
                .map(saleMapper::toEntity)
                .peek(s -> s.setProperty(property))
                .collect(Collectors.toList());
    }

    private List<Building> mapBuildings(Property property, PropertyApiResponse api) {
        return api.getBuilding().getBuildingInfos().stream()
                .map(externalBuildingMapper::toDTO)
                .map(buildingMapper::toEntity)
                .peek(b -> b.setProperty(property))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void ingestAddresses(String folio){
        try {
            Property property = propertyRepository.findByFolioAndCounty_Id(folio, countyService.miamiDade().getId()).orElse(null);
        if(property != null && property.getAddress() == null){
            PropertyApiResponse api = parse(miameDadaApiClient.importMiameDadePropertyDetails(folio).block());
            applyAddress(property, api);

            propertyRepository.save(property);

            log.info("Addresses refreshed for folio {}", folio);
        }
        } catch (Exception e) {
            log.error("failed to ingest addresses", e);
        }
    }

    private void applyAddress(Property property, PropertyApiResponse api) {
        List<SiteAddress> addresses = api.getSiteAddress();
        if(!addresses.isEmpty()){
            Address address = addressMapper.toEntity(externalAddressMapper.toDTO(addresses.get(0)));
            property.setAddress(address);
            address.setProperty(property);
        }
    }

    @Override
    @Transactional
    public void ingestBuildings(String folio){
        try {
            Property property = propertyRepository.findByFolioAndCounty_Id(folio, countyService.miamiDade().getId()).orElse(null);
            if(property != null && property.getBuildings() == null){
                PropertyApiResponse api = parse(miameDadaApiClient.importMiameDadePropertyDetails(folio).block());
                applyBuildings(property, api);

                propertyRepository.save(property);

                log.info("Buildings refreshed for folio {}", folio);
            }
        } catch (Exception e) {
            log.error("failed to ingest buildings", e);
        }
    }

    private void applyBuildings(Property property, PropertyApiResponse api) {
        List<Building> buildings = mapBuildings(property, api);
        if(property.getBuildings() == null)
            property.setBuildings(new ArrayList<>());
        property.getBuildings().addAll(buildings);
    }

    @Override
    @Transactional
    public void ingestExtraFeatures(String folio){
        try {
            Property property = propertyRepository.findByFolioAndCounty_Id(folio, countyService.miamiDade().getId()).orElse(null);
            if(property != null && (property.getExtraFeatures() == null || property.getExtraFeatures().isEmpty())){
                PropertyApiResponse api = parse(miameDadaApiClient.importMiameDadePropertyDetails(folio).block());
                applyExtraFeatures(property, api);

                propertyRepository.save(property);

                log.info("extra features refreshed for folio {}", folio);
            }
        } catch (Exception e) {
            log.error("failed to ingest extra features", e);
            throw new RuntimeException("Failed to ingest extra features for folio " + folio, e);
        }
    }

    private void applyExtraFeatures(Property property, PropertyApiResponse api) {
        List<ExtraFeature> extraFeatures = api.getExtraFeature().getExtraFeatureInfos().stream()
            .map(externalExtraFeatureMapper::toDTO)
            .map(extraFeatureMapper::toEntity)
            .peek(e -> e.setProperty(property))
            .collect(Collectors.toList());
        log.info("extra feature ingested {}", extraFeatures);
        if(property.getExtraFeatures() == null)
            property.setExtraFeatures(new ArrayList<>());
        property.getExtraFeatures().addAll(extraFeatures);

        if(property.getExtraFeatures().isEmpty()){
            // A placeholder marks the property as checked so it is not fetched again.
            ExtraFeature feature = new ExtraFeature();
            feature.setProperty(property);
            feature.setDescription("no feature for this property");
            feature.setMessage("no feature for this property");
            property.getExtraFeatures().add(feature);
            log.info("inserting a temporarily feature");
        }
    }

    @Override
    @Transactional
    public void ingestSales(String folio){
        try {
            Property property = propertyRepository.findByFolioAndCounty_Id(folio, countyService.miamiDade().getId()).orElse(null);
        if(property != null && (property.getSales() == null || property.getSales().isEmpty())){
            PropertyApiResponse api = parse(miameDadaApiClient.importMiameDadePropertyDetails(folio).block());
            applySales(property, api);

            propertyRepository.save(property);

            log.info("sales refreshed for folio {}", folio);
        }
        } catch (Exception e) {
            log.error("failed to ingest sales", e);
        }
    }

    private void applySales(Property property, PropertyApiResponse api) {
        List<Sale> sales = mapSales(property, api);
        if(property.getSales() == null)
            property.setSales(new ArrayList<>());
        property.getSales().addAll(sales);
    }

    private void attachParcel(String folio, Property property) {
        log.info("--------------------attaching parcels----------------");

        if (folio == null) {
            log.warn("Skipping parcel attach: folio is null");
            return;
        }

        List<Parcel> parcels = parcelRepository.findByFolioAndProperty_County_Id(folio, property.getCounty().getId());

        if (parcels.isEmpty()) {
            log.warn("No parcels found for folio {}", folio);
            return;
        }

        for (Parcel parcel : parcels) {
            property.addParcel(parcel);
            log.debug("Attached parcel ID: {} to property folio: {}", parcel.getId(), folio);
        }

        log.info("Successfully attached {} parcels to folio {}", parcels.size(), folio);
    }
}
