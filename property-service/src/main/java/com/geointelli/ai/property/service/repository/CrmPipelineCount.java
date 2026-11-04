package com.geointelli.ai.property.service.repository;

import com.geointelli.ai.property.service.entity.CrmTransactionStage;

public interface CrmPipelineCount {
    CrmTransactionStage getStage();
    long getCount();
}
