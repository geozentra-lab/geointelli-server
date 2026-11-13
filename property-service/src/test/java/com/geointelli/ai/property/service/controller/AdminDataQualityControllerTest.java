package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.DataQualityIssueDTO;
import com.geointelli.ai.property.service.dto.DataQualityReportDTO;
import com.geointelli.ai.property.service.dto.DataQualityRuleSummaryDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.enums.DataQualityCategory;
import com.geointelli.ai.property.service.enums.DataQualityRule;
import com.geointelli.ai.property.service.service.DataQualityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDataQualityController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AdminDataQualityControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    DataQualityService dataQualityService;

    private static final String URL = "/api/admin/data-quality/issues";

    private static DataQualityReportDTO report() {
        DataQualityRuleSummaryDTO summary = new DataQualityRuleSummaryDTO();
        summary.setRule(DataQualityRule.SALE_DUPLICATE);
        summary.setCategory(DataQualityCategory.DUPLICATE);
        summary.setEntityType("SALE");
        summary.setSource("sales");
        summary.setDescription(DataQualityRule.SALE_DUPLICATE.getDescription());
        summary.setIssueCount(1);

        DataQualityReportDTO report = new DataQualityReportDTO();
        report.setGeneratedAt(Instant.parse("2026-11-08T10:00:00Z"));
        report.setSummary(List.of(summary));
        report.setNote("note");
        return report;
    }

    @Nested
    @DisplayName("Test Data Quality Issues Endpoint")
    class DataQualityIssuesTest {

        @Test
        @DisplayName("When no rule is given, then use page 0 and size 20 and return only the summary")
        void whenNoRule_thenReturnSummaryOnly() throws Exception {
            // Arrange
            when(dataQualityService.getReport(null, 0, 20)).thenReturn(report());

            // Act & Assert
            mockMvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary[0].rule").value("SALE_DUPLICATE"))
                    .andExpect(jsonPath("$.summary[0].category").value("DUPLICATE"))
                    .andExpect(jsonPath("$.summary[0].issueCount").value(1))
                    .andExpect(jsonPath("$.rule").doesNotExist())
                    .andExpect(jsonPath("$.issues").doesNotExist())
                    .andExpect(jsonPath("$.note").value("note"));
            verify(dataQualityService).getReport(null, 0, 20);
        }

        @Test
        @DisplayName("When a rule is given, then return 200 with its page of issues")
        void whenRuleGiven_thenReturnIssues() throws Exception {
            // Arrange
            DataQualityIssueDTO issue = new DataQualityIssueDTO();
            issue.setReference("SALE_DUPLICATE:SALE:9012");
            issue.setRule(DataQualityRule.SALE_DUPLICATE);
            issue.setEntityId(9012L);
            issue.setSourceCounty("Miami-Dade");
            DataQualityReportDTO report = report();
            report.setRule(DataQualityRule.SALE_DUPLICATE);
            report.setIssues(new PageResponse<>(List.of(issue), 0, 5, 1, 1, true, true));
            when(dataQualityService.getReport(DataQualityRule.SALE_DUPLICATE, 0, 5)).thenReturn(report);

            // Act & Assert
            mockMvc.perform(get(URL).param("rule", "SALE_DUPLICATE").param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rule").value("SALE_DUPLICATE"))
                    .andExpect(jsonPath("$.issues.content[0].reference").value("SALE_DUPLICATE:SALE:9012"))
                    .andExpect(jsonPath("$.issues.content[0].sourceCounty").value("Miami-Dade"))
                    .andExpect(jsonPath("$.issues.totalElements").value(1));
        }

        @Test
        @DisplayName("When the rule is unknown, then return 400 without calling the service")
        void whenRuleUnknown_thenReturn400() throws Exception {
            // Act & Assert
            mockMvc.perform(get(URL).param("rule", "ABC"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"));
            verifyNoInteractions(dataQualityService);
        }

        @Test
        @DisplayName("When the service rejects the size, then return 400")
        void whenServiceRejectsSize_thenReturn400() throws Exception {
            // Arrange
            when(dataQualityService.getReport(null, 0, 101))
                    .thenThrow(new IllegalArgumentException("size must be between 1 and 100"));

            // Act & Assert
            mockMvc.perform(get(URL).param("size", "101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
        }

        @Test
        @DisplayName("When page is not a number, then return 400 without calling the service")
        void whenPageIsNotANumber_thenReturn400() throws Exception {
            // Act & Assert
            mockMvc.perform(get(URL).param("page", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"));
            verifyNoInteractions(dataQualityService);
        }
    }
}
