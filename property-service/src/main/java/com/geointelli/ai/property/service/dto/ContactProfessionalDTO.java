package com.geointelli.ai.property.service.dto;

/** Selectable REALTOR account; private email, phone and Keycloak subject are never listed. */
public record ContactProfessionalDTO(Long id, String name) {}

