package com.geointelli.ai.property.service.dto;

/** Internal, validated snapshot of related CRM IDs. Not supplied directly by an HTTP caller. */
public record CrmWorkLinks(Long clientId, Long leadId, Long propertyId, Long transactionId) {
}
