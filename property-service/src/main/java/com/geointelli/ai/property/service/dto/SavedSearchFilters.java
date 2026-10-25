package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/** Uses the existing property-search fields; pagination is supplied only when replaying results. */
@JsonIgnoreProperties({"page", "size"})
@Schema(description = "Same geography, property and sort fields as property search. "
    + "page and size are ignored here and never stored; supply them on the results endpoint. Unknown filter names are rejected.")
public class SavedSearchFilters extends PropertySearchRequest {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported search filter: " + name);
    }
}
