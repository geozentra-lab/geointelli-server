package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.geointelli.ai.property.service.dto.ImageBackfillJobDTO;
import com.geointelli.ai.property.service.dto.ImageBackfillResultDTO;
import com.geointelli.ai.property.service.entity.ImageBackfillJob;
import com.geointelli.ai.property.service.entity.ImageBackfillResult;

/** Maps job entities to API responses without exposing persistence relationships. */
@Mapper(componentModel = "spring")
public interface ImageBackfillJobMapper {
    @Mapping(target = "jobId", source = "id")
    @Mapping(target = "countyId", source = "county.id")
    @Mapping(target = "results", ignore = true)
    ImageBackfillJobDTO toDTO(ImageBackfillJob job);

    @Mapping(target = "propertyId", source = "property.id")
    @Mapping(target = "imageId", source = "image.id")
    ImageBackfillResultDTO toDTO(ImageBackfillResult result);
}
