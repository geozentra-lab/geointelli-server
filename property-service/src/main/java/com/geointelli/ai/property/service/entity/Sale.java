package com.geointelli.ai.property.service.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@ToString(exclude = "property")
@Table(name = "sales")
@NoArgsConstructor
@AllArgsConstructor
public class Sale extends AuditableEntity {

    @Id
    @GeneratedValue
    private Long id;

    // Sale information
    private Integer saleId;
    private LocalDate saleDate;
    private BigDecimal salePrice;
    @Column(name = "sale_type")
    private String saleType;

    // Recording information
    private String officialRecordBook;
    private String officialRecordPage;
    private String encodedRecordBookAndPage;

    // Instrument
    @Column(name = "instrument_number")
    private String instrumentNumber;
    private String saleInstrument;
    private Integer documentStamps;

    // Qualification
    private String qualifiedFlag;
    private String qualificationDescription;
    @Column(name = "qualifiedsyear")
    private Integer qualifiedSYear;
    private String qualifiedSourceCode;
    private String reasonCode;
    private String reviewCode;

    // Parties
    private String grantorName1;
    private String grantorName2;
    private String granteeName1;
    private String granteeName2;

    private Boolean vacantFlag;

    @Column(length = 1000)
    private String message;

    @ManyToOne
    @JoinColumn(name = "property_id")
    private Property property;
}
