package com.geointelli.ai.property.service.dto;

import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.geointelli.ai.property.service.entity.AlertCadence;
import com.geointelli.ai.property.service.entity.AlertEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/** Creates a stable target/event identity; PATCH edits name, cadence and paused state only. */
@Getter
@Schema(description = "POST requires name, eventType and exactly one of propertyId or savedSearchId. "
    + "NEW_MATCH requires savedSearchId. One event per rule. PATCH changes supplied name/cadence/paused fields; "
    + "target and eventType are immutable. Recreate the rule to change its target or trigger.")
public class AlertRuleRequest {
    @NotBlank(groups = AlertRuleCreate.class)
    @Size(max = 120)
    @Schema(example = "New Miami homes")
    private String name;

    @NotNull(groups = AlertRuleCreate.class)
    private AlertEventType eventType;

    @Positive
    @Schema(description = "Internal property ID; do not send a folio.")
    private Long propertyId;

    @Positive
    private Long savedSearchId;

    private AlertCadence cadence;
    private Boolean paused;

    @JsonIgnore
    private final Set<String> providedFields = new HashSet<>();

    public boolean hasField(String field) { return providedFields.contains(field); }

    @JsonSetter
    public void setName(String value) { name = value; providedFields.add("name"); }
    @JsonSetter
    public void setEventType(AlertEventType value) { eventType = value; providedFields.add("eventType"); }
    @JsonSetter
    public void setPropertyId(Long value) { propertyId = value; providedFields.add("propertyId"); }
    @JsonSetter
    public void setSavedSearchId(Long value) { savedSearchId = value; providedFields.add("savedSearchId"); }
    @JsonSetter
    public void setCadence(AlertCadence value) { cadence = value; providedFields.add("cadence"); }
    @JsonSetter
    public void setPaused(Boolean value) { paused = value; providedFields.add("paused"); }

    @AssertTrue(message = "Supplied name must not be blank; eventType, cadence and paused must not be null when supplied")
    @JsonIgnore
    public boolean isSuppliedFieldsValid() {
        return (!hasField("name") || (name != null && !name.isBlank()))
            && (!hasField("eventType") || eventType != null)
            && (!hasField("cadence") || cadence != null)
            && (!hasField("paused") || paused != null);
    }
}
