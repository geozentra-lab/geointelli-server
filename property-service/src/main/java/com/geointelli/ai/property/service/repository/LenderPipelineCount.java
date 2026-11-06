package com.geointelli.ai.property.service.repository;

import java.math.BigDecimal;
import com.geointelli.ai.property.service.entity.LenderApplicationStage;

/** Database aggregation, independent of application-list pagination. */
public interface LenderPipelineCount {
    LenderApplicationStage getStage();
    long getCount();
    BigDecimal getRequested();
    BigDecimal getApproved();
    BigDecimal getFunded();
}

