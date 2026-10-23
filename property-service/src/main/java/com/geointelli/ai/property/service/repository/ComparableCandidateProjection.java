package com.geointelli.ai.property.service.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Scalar query result that avoids loading property collections. */
public interface ComparableCandidateProjection {
    Long getPropertyId();
    String getFolio();
    Long getCountyId();
    String getAddress();
    String getCity();
    String getZip();
    String getDorCode();
    BigDecimal getBedrooms();
    BigDecimal getBathrooms();
    BigDecimal getLivingAreaSqft();
    String getYearBuilt();
    Long getSaleId();
    LocalDate getSaleDate();
    BigDecimal getSalePrice();
    String getQualifiedFlag();
    String getQualificationDescription();
    String getInstrumentNumber();
    String getMatchLevel();
    Double getDistanceMeters();
}
