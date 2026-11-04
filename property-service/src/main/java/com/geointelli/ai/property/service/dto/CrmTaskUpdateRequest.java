package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.geointelli.ai.property.service.entity.CrmTaskStatus;
import lombok.Getter;

/** PATCH changes supplied fields only; explicit null clears description or dueAt. */
@Getter
public class CrmTaskUpdateRequest {
    private String title;
    private String description;
    private Instant dueAt;
    private CrmTaskStatus status;
    @JsonIgnore private boolean titleProvided;
    @JsonIgnore private boolean descriptionProvided;
    @JsonIgnore private boolean dueAtProvided;
    @JsonIgnore private boolean statusProvided;
    @JsonSetter public void setTitle(String value) { title = value; titleProvided = true; }
    @JsonSetter public void setDescription(String value) { description = value; descriptionProvided = true; }
    @JsonSetter public void setDueAt(Instant value) { dueAt = value; dueAtProvided = true; }
    @JsonSetter public void setStatus(CrmTaskStatus value) { status = value; statusProvided = true; }
}
