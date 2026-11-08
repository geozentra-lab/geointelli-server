package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.DataQualityIssueDTO;
import com.geointelli.ai.property.service.dto.DataQualityReportDTO;
import com.geointelli.ai.property.service.dto.DataQualityRuleSummaryDTO;
import com.geointelli.ai.property.service.enums.DataQualityCategory;
import com.geointelli.ai.property.service.enums.DataQualityRule;
import com.geointelli.ai.property.service.repository.DataQualityIssueRow;
import com.geointelli.ai.property.service.repository.DataQualityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class DataQualityServiceImplTest {

    DataQualityRepository dataQualityRepository = mock(DataQualityRepository.class);

    DataQualityServiceImpl dataQualityService = new DataQualityServiceImpl(dataQualityRepository);

    private static DataQualityIssueRow row(long entityId, long propertyId, String detail,
                                           String sourceCounty, String sourceState, Long sourcePropertyId) {
        return new DataQualityIssueRow() {
            @Override public Long getEntityId() { return entityId; }
            @Override public Long getPropertyId() { return propertyId; }
            @Override public String getFolio() { return "F" + propertyId; }
            @Override public String getDetail() { return detail; }
            @Override public String getSourceCounty() { return sourceCounty; }
            @Override public String getSourceState() { return sourceState; }
            @Override public Long getSourcePropertyId() { return sourcePropertyId; }
        };
    }

    @Nested
    @DisplayName("GetReport Tests")
    class GetReport {

        @Test
        @DisplayName("When no rule is given, then return the summary of every rule only")
        void whenNoRule_thenReturnSummaryOnly() {
            // Arrange
            when(dataQualityRepository.countMissingAddress()).thenReturn(4L);
            when(dataQualityRepository.countSaleDuplicate()).thenReturn(12L);

            // Act
            DataQualityReportDTO report = dataQualityService.getReport(null, 0, 20);

            // Assert
            assertEquals(Arrays.asList(DataQualityRule.values()),
                    report.getSummary().stream().map(DataQualityRuleSummaryDTO::getRule).toList());
            DataQualityRuleSummaryDTO missingAddress = report.getSummary().get(0);
            assertEquals(DataQualityCategory.MISSING, missingAddress.getCategory());
            assertEquals("PROPERTY", missingAddress.getEntityType());
            assertEquals("addresses", missingAddress.getSource());
            assertEquals(DataQualityRule.PROPERTY_MISSING_ADDRESS.getDescription(), missingAddress.getDescription());
            assertEquals(4L, missingAddress.getIssueCount());
            assertEquals(12L, report.getSummary().get(DataQualityRule.SALE_DUPLICATE.ordinal()).getIssueCount());
            assertNull(report.getRule());
            assertNull(report.getIssues());
            assertNotNull(report.getGeneratedAt());
            assertEquals(DataQualityServiceImpl.NOTE, report.getNote());
            verify(dataQualityRepository, never()).findMissingAddress(anyInt(), anyLong());
            verify(dataQualityRepository, never()).findSaleDuplicate(anyInt(), anyLong());
        }

        @Test
        @DisplayName("When a rule is given, then return its page of issues with a reproducible reference and provenance")
        void whenRuleGiven_thenReturnIssuesWithReferenceAndProvenance() {
            // Arrange
            when(dataQualityRepository.countSaleDuplicate()).thenReturn(45L);
            when(dataQualityRepository.findSaleDuplicate(20, 20L)).thenReturn(List.of(
                    row(9012L, 345L, "Duplicate of sale 8800", "Miami-Dade", "FL", 123456L),
                    row(9013L, 346L, "Duplicate of sale 8801", null, null, null)));

            // Act
            DataQualityReportDTO report = dataQualityService.getReport(DataQualityRule.SALE_DUPLICATE, 1, 20);

            // Assert
            assertEquals(DataQualityRule.SALE_DUPLICATE, report.getRule());
            DataQualityIssueDTO issue = report.getIssues().content().get(0);
            assertEquals("SALE_DUPLICATE:SALE:9012", issue.getReference());
            assertEquals(DataQualityRule.SALE_DUPLICATE, issue.getRule());
            assertEquals(DataQualityCategory.DUPLICATE, issue.getCategory());
            assertEquals("SALE", issue.getEntityType());
            assertEquals(9012L, issue.getEntityId());
            assertEquals(345L, issue.getPropertyId());
            assertEquals("F345", issue.getFolio());
            assertEquals("Duplicate of sale 8800", issue.getDetail());
            assertEquals("Miami-Dade", issue.getSourceCounty());
            assertEquals("FL", issue.getSourceState());
            assertEquals(123456L, issue.getSourcePropertyId());

            DataQualityIssueDTO withoutMapping = report.getIssues().content().get(1);
            assertNull(withoutMapping.getSourceCounty());
            assertNull(withoutMapping.getSourcePropertyId());

            assertEquals(1, report.getIssues().page());
            assertEquals(20, report.getIssues().size());
            assertEquals(45L, report.getIssues().totalElements());
            assertEquals(3L, report.getIssues().totalPages());
            assertFalse(report.getIssues().first());
            assertFalse(report.getIssues().last());
        }

        @Test
        @DisplayName("When the rule has no issue, then return zero counts and an empty last page")
        void whenNoIssue_thenReturnEmptyPage() {
            // Act
            DataQualityReportDTO report = dataQualityService.getReport(DataQualityRule.ASSESSMENT_STALE, 0, 20);

            // Assert
            assertTrue(report.getSummary().stream().allMatch(summary -> summary.getIssueCount() == 0));
            assertTrue(report.getIssues().content().isEmpty());
            assertEquals(0L, report.getIssues().totalElements());
            assertEquals(0L, report.getIssues().totalPages());
            assertTrue(report.getIssues().first());
            assertTrue(report.getIssues().last());
        }

        @Test
        @DisplayName("When the page is past the end, then return an empty last page")
        void whenPagePastEnd_thenReturnEmptyLastPage() {
            // Arrange
            when(dataQualityRepository.countMissingParcel()).thenReturn(5L);

            // Act
            DataQualityReportDTO report = dataQualityService.getReport(DataQualityRule.PROPERTY_MISSING_PARCEL, 3, 20);

            // Assert
            verify(dataQualityRepository).findMissingParcel(20, 60L);
            assertTrue(report.getIssues().content().isEmpty());
            assertEquals(5L, report.getIssues().totalElements());
            assertTrue(report.getIssues().last());
        }

        @ParameterizedTest
        @EnumSource(DataQualityRule.class)
        @DisplayName("When any rule is requested, then it is counted and listed")
        void whenAnyRuleRequested_thenReturnItsPage(DataQualityRule rule) {
            // Act
            DataQualityReportDTO report = dataQualityService.getReport(rule, 0, 10);

            // Assert
            assertEquals(rule, report.getRule());
            assertNotNull(report.getIssues());
            assertEquals(DataQualityRule.values().length, report.getSummary().size());
        }

        @Test
        @DisplayName("When page or size is out of range, then throw IllegalArgumentException without querying")
        void whenPageOrSizeIsInvalid_thenThrowIllegalArgumentException() {
            // Act & Assert
            IllegalArgumentException page = assertThrows(IllegalArgumentException.class,
                    () -> dataQualityService.getReport(null, -1, 20));
            assertEquals("page must be 0 or greater", page.getMessage());

            IllegalArgumentException size = assertThrows(IllegalArgumentException.class,
                    () -> dataQualityService.getReport(DataQualityRule.SALE_DUPLICATE, 0, 0));
            assertEquals("size must be between 1 and 100", size.getMessage());

            assertThrows(IllegalArgumentException.class,
                    () -> dataQualityService.getReport(null, 0, DataQualityServiceImpl.MAX_PAGE_SIZE + 1));
            verifyNoInteractions(dataQualityRepository);
        }
    }
}
