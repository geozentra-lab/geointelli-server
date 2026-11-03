package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.geointelli.ai.property.service.dto.CrmActivityDTO;
import com.geointelli.ai.property.service.dto.CrmClientDTO;
import com.geointelli.ai.property.service.dto.CrmLeadDTO;
import com.geointelli.ai.property.service.entity.CrmActivity;
import com.geointelli.ai.property.service.entity.CrmClient;
import com.geointelli.ai.property.service.entity.CrmLead;

/** Maps persisted CRM records without exposing their owner or submission credentials. */
@Mapper(componentModel = "spring")
public interface CrmMapper {
    CrmClientDTO toDTO(CrmClient client);
    @Mapping(target = "clientId", source = "client.id")
    CrmLeadDTO toDTO(CrmLead lead);
    CrmActivityDTO toDTO(CrmActivity activity);
}
