package com.geointelli.ai.property.service.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.client.MiameDadeApiClient;
import com.geointelli.ai.property.service.client.dto.PropertyApiResponse;
import com.geointelli.ai.property.service.client.dto.SiteAddress;
import com.geointelli.ai.property.service.entity.Address;
import com.geointelli.ai.property.service.entity.Assessment;
import com.geointelli.ai.property.service.entity.Building;
import com.geointelli.ai.property.service.entity.Land;
import com.geointelli.ai.property.service.entity.Owner;
import com.geointelli.ai.property.service.entity.Parcel;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.Sale;
import com.geointelli.ai.property.service.entity.Tax;
import com.geointelli.ai.property.service.mapper.AddressMapper;
import com.geointelli.ai.property.service.mapper.AssessmentMapper;
import com.geointelli.ai.property.service.mapper.BuildingMapper;
import com.geointelli.ai.property.service.mapper.LandMapper;
import com.geointelli.ai.property.service.mapper.OwnerMapper;
import com.geointelli.ai.property.service.mapper.PropertyMapper;
import com.geointelli.ai.property.service.mapper.SaleMapper;
import com.geointelli.ai.property.service.mapper.TaxMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalAddressMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalAssessmentMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalBuildingMapper;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyIngestionServiceImpl implements PropertyIngestionService {
    private final MiameDadeApiClient miameDadaApiClient;
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
    private final PropertyService propertyService;

    @Override
    @Transactional
    public void ingest(String folio) {
        try {
            Property existingProperty = propertyRepository.findByFolio(folio).orElse(null);
            if(existingProperty != null){
                log.info("property already found with this folio");
                return;
            }

            String response = miameDadaApiClient.importMiameDadePropertyDetails(folio).block();

            PropertyApiResponse api = objectMapper.readValue(response, PropertyApiResponse.class);
            System.out.println("------------response property:---------------" + api.getPropertyInfo());

            Property property = mapProperty(api);
            System.out.println("--------------------property:-----------------" + property);
            linkEntities(property, api);
            

            log.info("-------------------saving the property-----------------------");
            Property savedProperty = propertyService.saveProperty(property);

            attachParcel(folio, savedProperty);
            log.info("---------------------Ingested folio {}------------------", folio);

        } catch (Exception e) {
            log.error("Failed ingest folio {}", folio, e);
        }
    }

    // public Mono<Void> ingestReactive(String folio){

    //     return miameDadaApiClient.get()
    //             .uri(uriBuilder -> uriBuilder
    //                     .queryParam("folioNumber", folio)
    //                     .build())
    //             .retrieve()
    //             .bodyToMono(PropertyInfo.class)

    //             .retryWhen(
    //                 Retry.backoff(3, Duration.ofSeconds(2))
    //                     .maxBackoff(Duration.ofSeconds(10))
    //             )

    //             .doOnNext(propertyInfo -> {
    //                 saveProperty(propertyInfo);
    //             })

    //             .then();
    // }

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
        .collect(Collectors.toList());;

        owners.forEach(o -> {
            if (o.getProperties() == null) o.setProperties(new ArrayList<>());
            o.getProperties().add(property);
        });

        List<Assessment> assessments = api.getAssessment().getAssessmentInfos().stream()
            .map(externalAssessmentMapper::toDTO)
            .map(assessmentMapper::toEntity)
            .peek(a -> a.setProperty(property))
            .collect(Collectors.toList());
        System.out.println("------------response assessment:---------------" + assessments);


        List<Sale> sales = api.getSalesInfos().stream()
                .map(externalSaleMapper::toDTO)
                .map(saleMapper::toEntity)
                .peek(s -> s.setProperty(property))
                .collect(Collectors.toList());

        List<Land> lands = api.getLand().getLandlines().stream()
                .map(externalLandMapper::toDTO)
                .map(landMapper::toEntity)
                .peek(l -> l.setProperty(property))
                .collect(Collectors.toList());
        System.out.println("------------response land:---------------" + lands);


        List<Tax> taxes = api.getTaxable().getTaxableInfos().stream()
                .map(externalTaxMapper::toDTO)
                .map(taxMapper::toEntity)
                .peek(t -> t.setProperty(property))
                .collect(Collectors.toList());

        List<Building> buildings = api.getBuilding().getBuildingInfos().stream()
                .map(externalBuildingMapper::toDTO)
                .map(buildingMapper::toEntity)
                .peek(b -> b.setProperty(property))
                .collect(Collectors.toList());

        List<SiteAddress> addresses = api.getSiteAddress();     
        if(!addresses.isEmpty()){
            Address address = addressMapper.toEntity(externalAddressMapper.toDTO(addresses.get(0)));
            property.setAddress(address);
            System.out.println("----------------------address:---------"+address);
        }   
        
        property.setOwners(owners);
        if(property.getAssessments() == null)
            property.setAssessments(new ArrayList<>());
        property.getAssessments().addAll(assessments);
        // property.setAssessments(assessments);

        // property.setSales(sales);
        if(property.getSales() == null)
            property.setSales(new ArrayList<>());
        property.getSales().addAll(sales);

        // property.setLands(lands);
        if(property.getLands() == null)
            property.setLands(new ArrayList<>());
        property.getLands().addAll(lands);

        // property.setTaxes(taxes);
        if(property.getTaxes() == null)
            property.setTaxes(new ArrayList<>());
        property.getTaxes().addAll(taxes);

        if(property.getBuildings() == null)
            property.setBuildings(new ArrayList<>());
        property.getBuildings().addAll(buildings);
        // property.setBuildings(buildings);
    }

    @Override
    @Transactional
    public void ingestAddresses(String folio){
        try {
            Property property = propertyRepository.findByFolio(folio).orElse(null);
        if(property != null && property.getAddress() == null){
            String response = miameDadaApiClient.importMiameDadePropertyDetails(folio).block();
            PropertyApiResponse api = objectMapper.readValue(response, PropertyApiResponse.class);
            List<SiteAddress> addresses = api.getSiteAddress();     
            if(!addresses.isEmpty()){
                Address address = addressMapper.toEntity(externalAddressMapper.toDTO(addresses.get(0)));
                property.setAddress(address);
                address.setProperty(property);
                System.out.println("----------------------address:---------"+address);
            }
            
            propertyRepository.save(property);

            log.info("Addresses refreshed for folio {}", folio);
        }
        } catch (Exception e) {
            log.error("failed to ingest addresses", e);
        }
    }

    @Override
    @Transactional
    public void ingestBuildings(String folio){
        try {
            Property property = propertyRepository.findByFolio(folio).orElse(null);
        if(property != null && property.getBuildings() == null){
            String response = miameDadaApiClient.importMiameDadePropertyDetails(folio).block();
            PropertyApiResponse api = objectMapper.readValue(response, PropertyApiResponse.class);
            List<Building> buildings = api.getBuilding().getBuildingInfos().stream()
                .map(externalBuildingMapper::toDTO)
                .map(buildingMapper::toEntity)
                .peek(b -> b.setProperty(property))
                .collect(Collectors.toList());
            if(property.getBuildings() == null)
                property.setBuildings(new ArrayList<>());
            property.getBuildings().addAll(buildings); 
            
            propertyRepository.save(property);

            log.info("Buildings refreshed for folio {}", folio);
        }
        } catch (Exception e) {
            log.error("failed to ingest buildings", e);
        }
    }

    private void attachParcel(String folio, Property property) {
        log.info("--------------------attaching parcels----------------");
        
        if (folio == null) {
            log.warn("Skipping parcel attach: folio is null");
            return;
        }

        List<Parcel> parcels = parcelRepository.findAllByFolio(folio);

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
