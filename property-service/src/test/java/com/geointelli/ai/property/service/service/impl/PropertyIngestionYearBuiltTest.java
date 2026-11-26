package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.client.MiamiDadeApiClient;
import com.geointelli.ai.property.service.client.dto.BuildingInfo;
import com.geointelli.ai.property.service.client.dto.PropertyApiResponse;
import com.geointelli.ai.property.service.client.dto.PropertyInfo;
import com.geointelli.ai.property.service.dto.BuildingDTO;
import com.geointelli.ai.property.service.dto.PropertyDTO;
import com.geointelli.ai.property.service.entity.Building;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.mapper.AssessmentMapper;
import com.geointelli.ai.property.service.mapper.BuildingMapper;
import com.geointelli.ai.property.service.mapper.LandMapper;
import com.geointelli.ai.property.service.mapper.OwnerMapper;
import com.geointelli.ai.property.service.mapper.PropertyMapper;
import com.geointelli.ai.property.service.mapper.SaleMapper;
import com.geointelli.ai.property.service.mapper.TaxMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalAssessmentMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalBuildingMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalLandMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalOwnerMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalPropertyMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalSaleMapper;
import com.geointelli.ai.property.service.mapper.external.ExternalTaxMapper;
import com.geointelli.ai.property.service.repository.ParcelRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.CountyService;
import com.geointelli.ai.property.service.service.PropertyService;
import reactor.core.publisher.Mono;

/** A property from the Miami-Dade API whose year built reads "Multiple (See Building Info.)". */
@ExtendWith(MockitoExtension.class)
class PropertyIngestionYearBuiltTest {
    @Mock MiamiDadeApiClient client;
    @Mock ObjectMapper objectMapper;
    @Mock ParcelRepository parcelRepository;
    @Mock PropertyRepository propertyRepository;
    @Mock PropertyMapper propertyMapper;
    @Mock ExternalPropertyMapper externalPropertyMapper;
    @Mock OwnerMapper ownerMapper;
    @Mock ExternalOwnerMapper externalOwnerMapper;
    @Mock AssessmentMapper assessmentMapper;
    @Mock ExternalAssessmentMapper externalAssessmentMapper;
    @Mock SaleMapper saleMapper;
    @Mock ExternalSaleMapper externalSaleMapper;
    @Mock LandMapper landMapper;
    @Mock ExternalLandMapper externalLandMapper;
    @Mock TaxMapper taxMapper;
    @Mock ExternalTaxMapper externalTaxMapper;
    @Mock BuildingMapper buildingMapper;
    @Mock ExternalBuildingMapper externalBuildingMapper;
    @Mock PropertyService propertyService;
    @Mock CountyService countyService;
    @InjectMocks PropertyIngestionServiceImpl service;

    private static Building building(int buildingNo, int actual) {
        Building building = new Building();
        building.setRollYear(2025);
        building.setBuildingNo(buildingNo);
        building.setSegNo(1);
        building.setActual(actual);
        return building;
    }

    private static PropertyApiResponse response() {
        PropertyApiResponse api = new PropertyApiResponse();
        api.setPropertyInfo(new PropertyInfo());
        api.setOwnerInfos(List.of());
        api.setSalesInfos(List.of());
        api.setSiteAddress(List.of());
        var assessment = new com.geointelli.ai.property.service.client.dto.Assessment();
        assessment.setAssessmentInfos(List.of());
        api.setAssessment(assessment);
        var land = new com.geointelli.ai.property.service.client.dto.Land();
        land.setLandlines(List.of());
        api.setLand(land);
        var taxable = new com.geointelli.ai.property.service.client.dto.Taxable();
        taxable.setTaxableInfos(List.of());
        api.setTaxable(taxable);
        var buildings = new com.geointelli.ai.property.service.client.dto.Building();
        buildings.setBuildingInfos(List.of(new BuildingInfo(), new BuildingInfo()));
        api.setBuilding(buildings);
        return api;
    }

    @Test
    @DisplayName("The property is saved with its first building's year instead of the county's text")
    void savesFirstBuildingYear() throws Exception {
        County miamiDade = new County();
        miamiDade.setId(10L);
        when(countyService.miamiDade()).thenReturn(miamiDade);
        when(propertyRepository.findByFolioAndCounty_Id("0130000000010", 10L)).thenReturn(Optional.empty());
        when(client.importMiameDadePropertyDetails("0130000000010")).thenReturn(Mono.just("{}"));
        PropertyApiResponse api = response();
        when(objectMapper.readValue("{}", PropertyApiResponse.class)).thenReturn(api);
        PropertyDTO dto = new PropertyDTO();
        when(externalPropertyMapper.toDTO(api.getPropertyInfo())).thenReturn(dto);
        Property mapped = new Property();
        mapped.setYearBuilt("Multiple (See Building Info.)");
        when(propertyMapper.toEntity(dto)).thenReturn(mapped);
        when(externalBuildingMapper.toDTO(any(BuildingInfo.class))).thenReturn(new BuildingDTO());
        // the second building is older: the first one still gives the year, as the county does
        when(buildingMapper.toEntity(any(BuildingDTO.class))).thenReturn(building(1, 1970), building(2, 1960));
        when(propertyService.saveProperty(any(Property.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.ingest("0130000000010");

        ArgumentCaptor<Property> saved = ArgumentCaptor.forClass(Property.class);
        verify(propertyService).saveProperty(saved.capture());
        assertThat(saved.getValue().getYearBuilt()).isEqualTo("1970");
        assertThat(saved.getValue().getBuildings()).hasSize(2);
    }
}
