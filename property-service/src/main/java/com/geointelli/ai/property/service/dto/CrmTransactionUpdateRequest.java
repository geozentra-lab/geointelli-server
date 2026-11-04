package com.geointelli.ai.property.service.dto;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.geointelli.ai.property.service.entity.CrmTransactionStage;
import lombok.Getter;

/** Associations stay fixed; description and expectedCloseDate can be explicitly cleared. */
@Getter
public class CrmTransactionUpdateRequest {
    private String title;
    private String description;
    private LocalDate expectedCloseDate;
    private CrmTransactionStage stage;
    @JsonIgnore private boolean titleProvided;
    @JsonIgnore private boolean descriptionProvided;
    @JsonIgnore private boolean expectedCloseDateProvided;
    @JsonIgnore private boolean stageProvided;
    @JsonSetter public void setTitle(String value) { title = value; titleProvided = true; }
    @JsonSetter public void setDescription(String value) { description = value; descriptionProvided = true; }
    @JsonSetter public void setExpectedCloseDate(LocalDate value) { expectedCloseDate = value; expectedCloseDateProvided = true; }
    @JsonSetter public void setStage(CrmTransactionStage value) { stage = value; stageProvided = true; }
}
