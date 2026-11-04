package com.geointelli.ai.property.service.mapper;

import java.time.Instant;
import org.springframework.stereotype.Component;
import com.geointelli.ai.property.service.dto.CrmTaskDTO;
import com.geointelli.ai.property.service.dto.CrmActivityDTO;
import com.geointelli.ai.property.service.dto.CrmTransactionDTO;
import com.geointelli.ai.property.service.entity.CrmTask;
import com.geointelli.ai.property.service.entity.CrmActivity;
import com.geointelli.ai.property.service.entity.CrmTransaction;
import lombok.RequiredArgsConstructor;

/** Maps work records without loading related entities or inferring financial outcomes. */
@Component
@RequiredArgsConstructor
public class CrmWorkMapper {
    private final CrmMapper crmMapper;

    public CrmActivityDTO toDTO(CrmActivity activity) { return crmMapper.toDTO(activity); }
    public CrmTaskDTO toDTO(CrmTask task, Instant now) {
        boolean overdue = task.getStatus().isActive() && task.getDueAt() != null && task.getDueAt().isBefore(now);
        return new CrmTaskDTO(task.getId(), task.getTitle(), task.getDescription(), task.getStatus(),
            task.getDueAt(), task.getCompletedAt(), overdue, task.getClientId(), task.getLeadId(),
            task.getPropertyId(), task.getTransactionId(), task.getCreatedAt(), task.getUpdatedAt());
    }

    public CrmTransactionDTO toDTO(CrmTransaction value) {
        return new CrmTransactionDTO(value.getId(), value.getTitle(), value.getDescription(), value.getStage(),
            value.getClientId(), value.getLeadId(), value.getPropertyId(), value.getExpectedCloseDate(),
            value.getClosedAt(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
