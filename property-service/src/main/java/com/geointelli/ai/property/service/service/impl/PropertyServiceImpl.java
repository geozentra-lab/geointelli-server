package com.geointelli.ai.property.service.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.entity.Address;
import com.geointelli.ai.property.service.entity.Assessment;
import com.geointelli.ai.property.service.entity.Building;
import com.geointelli.ai.property.service.entity.Land;
import com.geointelli.ai.property.service.entity.Owner;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.Sale;
import com.geointelli.ai.property.service.entity.Tax;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
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
import com.geointelli.ai.property.service.service.PropertyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.client.MiameDadeApiClient;
import com.geointelli.ai.property.service.client.dto.PropertyApiResponse;
import com.geointelli.ai.property.service.client.dto.SiteAddress;
import com.geointelli.ai.property.service.dto.PropertyDTO;

import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Service
@Data
@Slf4j
public class PropertyServiceImpl implements PropertyService {
    private final PropertyRepository propertyRepository;
    private final MiameDadeApiClient miameDadaApiClient;
    private final ObjectMapper objectMapper;
    private final PropertyMapper propertyMapper;
    private final ExternalPropertyMapper externalPropertyMapper;
    private final ParcelRepository parcelRepository;
    private final OwnerRepository ownerRepository;
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

    @Transactional
    @Override
    public Property saveProperty(Property property) {
        Property existing = propertyRepository.findByFolio(property.getFolio()).orElse(null);
        if (existing != null) {
            property.setId(existing.getId());
        }
        return propertyRepository.save(property);
    }

    @Override
    public PropertyDTO getByFolio(String folio) {
        Property property = propertyRepository.findByFolio(folio).orElseThrow(() -> new PropertyNotFoundException("No property found with folio: "+ folio));
        return propertyMapper.toDTO(property);
    }

    @Override
    public PropertyDTO getByFolioAPI(String folio) {
        try {
            String response = miameDadaApiClient.importMiameDadePropertyDetails(folio).block();

            PropertyApiResponse api = objectMapper.readValue(response, PropertyApiResponse.class);
            Property property = propertyMapper.toEntity(externalPropertyMapper.toDTO(api.getPropertyInfo()));
            linkEntities(property, api);
            return propertyMapper.toDTO(property);
        } 
        catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }

    private void linkEntities(Property property, PropertyApiResponse api) {

        List<Owner> owners = api.getOwnerInfos().stream()
        .map(externalOwnerMapper::toDTO)
        .map(ownerMapper::toEntity)
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
        System.out.println("------------response building:---------------" + api.getBuilding());        

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
    public List<String> getAllFolios() {
        return propertyRepository.findAllFolios();
    }

    @Override
    public List<Long> getAllIds() {
        return propertyRepository.findAllIds();
    }
}
