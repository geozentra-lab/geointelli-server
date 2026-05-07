package com.geointelli.ai.property.service.mapper.external;
import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.client.dto.PropertyInfo;
import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.config.StringSanitizer;
import com.geointelli.ai.property.service.dto.PropertyDTO;

@Mapper(componentModel = "spring",
        uses = {
            ExternalOwnerMapper.class,
            ExternalAddressMapper.class,
            ExternalAssessmentMapper.class,
            ExternalBuildingMapper.class,
            ExternalLandMapper.class,
            ExternalSaleMapper.class,
            ExternalTaxMapper.class,
            StringSanitizer.class,
        }, config = IgnoreUnmappedMapperConfig.class)
public interface ExternalPropertyMapper
 {
    PropertyDTO toDTO(PropertyInfo propertyInfo);
}
