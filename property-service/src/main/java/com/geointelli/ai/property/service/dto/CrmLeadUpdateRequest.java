package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.geointelli.ai.property.service.entity.LeadStage;
import lombok.Getter;

/** The contact is immutable; property and notes can be explicitly cleared. */
@Getter
public class CrmLeadUpdateRequest {
    private String title;
    private String source;
    private String notes;
    private Long propertyId;
    private LeadStage stage;
    @JsonIgnore private boolean titleProvided;
    @JsonIgnore private boolean sourceProvided;
    @JsonIgnore private boolean notesProvided;
    @JsonIgnore private boolean propertyIdProvided;
    @JsonIgnore private boolean stageProvided;

    @JsonSetter public void setTitle(String value) { title = value; titleProvided = true; }
    @JsonSetter public void setSource(String value) { source = value; sourceProvided = true; }
    @JsonSetter public void setNotes(String value) { notes = value; notesProvided = true; }
    @JsonSetter public void setPropertyId(Long value) { propertyId = value; propertyIdProvided = true; }
    @JsonSetter public void setStage(LeadStage value) { stage = value; stageProvided = true; }
}
