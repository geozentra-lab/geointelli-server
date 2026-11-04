package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;

/** Omitted fields stay unchanged; explicit null clears an optional field. */
@Getter
public class CrmClientUpdateRequest {
    private String name;
    private String email;
    private String phone;
    private String notes;
    @JsonIgnore private boolean nameProvided;
    @JsonIgnore private boolean emailProvided;
    @JsonIgnore private boolean phoneProvided;
    @JsonIgnore private boolean notesProvided;

    @JsonSetter public void setName(String value) { name = value; nameProvided = true; }
    @JsonSetter public void setEmail(String value) { email = value; emailProvided = true; }
    @JsonSetter public void setPhone(String value) { phone = value; phoneProvided = true; }
    @JsonSetter public void setNotes(String value) { notes = value; notesProvided = true; }
}
