package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.AlertSearchDefinitionDTO;
import com.geointelli.ai.property.service.entity.AlertRule;

public interface AlertSearchDefinitionService {
    /** Reads supported, owned saved filters and produces stable membership and configuration identities. */
    AlertSearchDefinitionDTO resolve(AlertRule rule);
}
