package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/** PATCH leaves omitted fields unchanged; an explicit null description clears it. */
@Getter
@Schema(description = "name is required on POST. PATCH changes only supplied fields; description:null clears it.")
public class PortfolioRequest {
    @NotBlank(groups = PortfolioCreate.class)
    @Size(max = 120)
    @Schema(example = "Florida rentals")
    private String name;

    @Size(max = 2000)
    @Schema(example = "Long-term residential investments")
    private String description;

    @JsonIgnore
    private boolean nameProvided;
    @JsonIgnore
    private boolean descriptionProvided;

    @JsonSetter
    public void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    @JsonSetter
    public void setDescription(String description) {
        this.description = description;
        this.descriptionProvided = true;
    }

    @AssertTrue(message = "name must not be blank or null when supplied")
    @JsonIgnore
    public boolean isNameValid() {
        return !nameProvided || (name != null && !name.isBlank());
    }
}
