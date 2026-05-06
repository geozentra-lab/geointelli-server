package com.geointelli.ai.property.service.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "property_value_predictions")
@Data
public class PropertyValuePrediction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "predicted_price", precision = 38, scale = 2)
    private BigDecimal predictedPrice;

    @Column(name = "predicted_price_low", precision = 38, scale = 2)
    private BigDecimal predictedPriceLow;

    @Column(name = "predicted_price_high", precision = 38, scale = 2)
    private BigDecimal predictedPriceHigh;

    @Column(name = "last_sale_price", precision = 38, scale = 2)
    private BigDecimal lastSalePrice;

    @Column(name = "last_sale_date")
    private LocalDate lastSaleDate;

    @Column(name = "confidence_score")
    private Integer confidenceScore;

    @Column(name = "prediction_date")
    private LocalDate predictionDate;

    @Column(name = "model_version", length = 10)
    private String modelVersion;
}
