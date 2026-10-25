package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.geointelli.ai.property.service.entity.SavedSearchCadence;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/** A PATCH replaces supplied filter definitions as a whole, preserving all omitted metadata fields. */
@Getter
@Schema(description = "POST requires name and filters. Defaults: DAILY, paused=false. "
    + "PATCH changes only supplied fields; filters replaces the entire definition. Explicit null is not allowed.")
public class SavedSearchRequest {
    @NotBlank(groups = SavedSearchCreate.class)
    @Size(max = 120)
    @Schema(example = "Miami family homes")
    private String name;

    @NotNull(groups = SavedSearchCreate.class)
    @Valid
    private SavedSearchFilters filters;

    private SavedSearchCadence cadence;
    private Boolean paused;

    @JsonIgnore
    private boolean nameProvided;
    @JsonIgnore
    private boolean filtersProvided;
    @JsonIgnore
    private boolean cadenceProvided;
    @JsonIgnore
    private boolean pausedProvided;

    @JsonSetter
    public void setName(String value) { name = value; nameProvided = true; }
    @JsonSetter
    public void setFilters(SavedSearchFilters value) { filters = value; filtersProvided = true; }
    @JsonSetter
    public void setCadence(SavedSearchCadence value) { cadence = value; cadenceProvided = true; }
    @JsonSetter
    public void setPaused(Boolean value) { paused = value; pausedProvided = true; }

    @AssertTrue(message = "Supplied name must not be blank and supplied fields must not be null")
    @JsonIgnore
    public boolean isSuppliedFieldsValid() {
        return (!nameProvided || (name != null && !name.isBlank()))
            && (!filtersProvided || filters != null)
            && (!cadenceProvided || cadence != null)
            && (!pausedProvided || paused != null);
    }
}
