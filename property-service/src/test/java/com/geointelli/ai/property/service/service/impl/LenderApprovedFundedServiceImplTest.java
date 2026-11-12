package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.LenderApprovedFundedCardDTO;
import com.geointelli.ai.property.service.dto.LenderApprovedFundedCardDTO.LoanMixEntry;
import com.geointelli.ai.property.service.entity.LenderLoanType;
import com.geointelli.ai.property.service.service.LenderApprovedFundedService;
import com.geointelli.ai.property.service.service.LenderOrganizationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the real aggregation queries on V24 + V34 in a disposable H2 database. */
@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:lender-dashboard-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({LenderApprovedFundedServiceImpl.class, LenderOrganizationServiceImpl.class,
        LenderApprovedFundedServiceImplTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LenderApprovedFundedServiceImplTest {

    /** "Today" is 15 October 2026 (UTC). */
    @TestConfiguration
    static class Beans {
        @Bean("lenderDashboardClock")
        Clock lenderDashboardClock() { return Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC); }
    }

    private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCT_31 = LocalDate.of(2026, 10, 31);

    @Autowired private LenderApprovedFundedService approvedFunded;
    @Autowired private LenderOrganizationService organizations;
    @Autowired private JdbcTemplate jdbc;
    private Long atlas;
    private Long other;

    @BeforeAll
    void schema() throws Exception {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE properties(id BIGINT PRIMARY KEY)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V24__add_lender_applications.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V34__add_lender_application_loan_type.sql"));
        }
    }

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM lender_applications");
        jdbc.update("DELETE FROM lender_organization_members");
        jdbc.update("DELETE FROM lender_organizations");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users VALUES(7),(8)");
        atlas = organizations.create(7L, "Atlas").id();
        other = organizations.create(8L, "Other bank").id();
    }

    private void approved(Long organization, String stage, String loanType, String amount, String approvedAt) {
        jdbc.update("INSERT INTO lender_applications(organization_id, created_by_user_id, applicant_name, "
                        + "requested_amount, stage, loan_type, approved_amount, approved_at) VALUES (?, 7, 'A', ?, ?, ?, ?, ?)",
                organization, new BigDecimal(amount), stage, loanType, new BigDecimal(amount),
                Timestamp.from(Instant.parse(approvedAt)));
    }

    private void funded(Long organization, String loanType, String approvedAmount, String approvedAt,
                        String fundedAmount, String fundedAt) {
        jdbc.update("INSERT INTO lender_applications(organization_id, created_by_user_id, applicant_name, "
                        + "requested_amount, stage, loan_type, approved_amount, approved_at, funded_amount, funded_at) "
                        + "VALUES (?, 7, 'A', ?, 'FUNDED', ?, ?, ?, ?, ?)",
                organization, new BigDecimal(approvedAmount), loanType, new BigDecimal(approvedAmount),
                Timestamp.from(Instant.parse(approvedAt)), new BigDecimal(fundedAmount),
                Timestamp.from(Instant.parse(fundedAt)));
    }

    /** Atlas in October 2026, plus records that must be left out. */
    private void givenAtlasApplications() {
        funded(atlas, "CONVENTIONAL", "300000", "2026-10-02T10:00:00Z", "280000", "2026-10-10T10:00:00Z");
        funded(atlas, "FHA", "200000", "2026-09-20T10:00:00Z", "190000", "2026-10-05T10:00:00Z");
        approved(atlas, "APPROVED", "VA", "150000", "2026-10-15T10:00:00Z");
        funded(atlas, "UNKNOWN", "100000", "2026-10-20T10:00:00Z", "100000", "2026-11-02T10:00:00Z");
        funded(atlas, "USDA", "60000", "2026-10-30T10:00:00Z", "50000", "2026-10-31T23:59:59Z");
        // Left out: a withdrawn approval, an application under review, another organization
        approved(atlas, "WITHDRAWN", "FHA", "50000", "2026-10-03T10:00:00Z");
        jdbc.update("INSERT INTO lender_applications(organization_id, created_by_user_id, applicant_name, "
                + "requested_amount, stage) VALUES (?, 7, 'A', 400000, 'UNDER_REVIEW')", atlas);
        funded(other, "CONVENTIONAL", "900000", "2026-10-02T10:00:00Z", "900000", "2026-10-03T10:00:00Z");
    }

    private static void assertMix(List<LoanMixEntry> mix, long[] counts, String[] amounts) {
        assertEquals(List.of(LenderLoanType.values()), mix.stream().map(LoanMixEntry::loanType).toList());
        for (int i = 0; i < counts.length; i++) {
            assertEquals(counts[i], mix.get(i).count(), "count of " + mix.get(i).loanType());
            assertEquals(new BigDecimal(amounts[i]), mix.get(i).totalAmount(), "amount of " + mix.get(i).loanType());
        }
    }

    @Test
    @DisplayName("When a period is given, then approved uses approvedAt, funded uses fundedAt and each application counts once")
    void whenPeriod_thenApprovedAndFundedUseTheirOwnDate() {
        // Arrange
        givenAtlasApplications();

        // Act
        LenderApprovedFundedCardDTO card = approvedFunded.getCard(7L, atlas, OCT_1, OCT_31);

        // Assert
        assertEquals(atlas, card.organizationId());
        assertEquals("USD", card.currency());
        assertEquals(LenderApprovedFundedCardDTO.STATUS_OK, card.status());
        assertNull(card.error());
        assertEquals(new LenderApprovedFundedCardDTO.Period(OCT_1, OCT_31, "UTC"), card.period());
        // Approved in October and still APPROVED/FUNDED: 300 000 + 150 000 + 100 000 + 60 000
        assertEquals(4, card.approved().count());
        assertEquals(new BigDecimal("610000.00"), card.approved().totalAmount());
        // Funded in October: 280 000 + 190 000 + 50 000 (the last second of the 31st is included)
        assertEquals(3, card.funded().count());
        assertEquals(new BigDecimal("520000.00"), card.funded().totalAmount());
    }

    @Test
    @DisplayName("When applications are funded, then the loan mix lists every type and adds up to funded")
    void whenFunded_thenLoanMixAddsUpToFunded() {
        // Arrange
        givenAtlasApplications();

        // Act
        LenderApprovedFundedCardDTO card = approvedFunded.getCard(7L, atlas, OCT_1, OCT_31);

        // Assert
        assertMix(card.fundedLoanMix(), new long[]{1, 1, 0, 1, 0},
                new String[]{"280000.00", "190000.00", "0.00", "50000.00", "0.00"});
        assertEquals(card.funded().count(), card.fundedLoanMix().stream().mapToLong(LoanMixEntry::count).sum());
        assertEquals(card.funded().totalAmount(), card.fundedLoanMix().stream()
                .map(LoanMixEntry::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    @DisplayName("When a funded application has no loan type, then it is shown as UNKNOWN")
    void whenNoLoanType_thenUnknown() {
        // Arrange
        givenAtlasApplications();

        // Act
        LenderApprovedFundedCardDTO card = approvedFunded.getCard(7L, atlas,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        // Assert
        assertEquals(0, card.approved().count());
        assertEquals(1, card.funded().count());
        assertMix(card.fundedLoanMix(), new long[]{0, 0, 0, 0, 1},
                new String[]{"0.00", "0.00", "0.00", "0.00", "100000.00"});
    }

    @Test
    @DisplayName("When no dates are given, then use the current UTC month")
    void whenNoDates_thenCurrentMonth() {
        // Arrange
        givenAtlasApplications();

        // Act
        LenderApprovedFundedCardDTO card = approvedFunded.getCard(7L, atlas, null, null);

        // Assert
        assertEquals(OCT_1, card.period().fromDate());
        assertEquals(OCT_31, card.period().toDate());
        assertEquals(4, card.approved().count());
        assertEquals(Instant.parse("2026-10-15T12:00:00Z"), card.generatedAt());
    }

    @Test
    @DisplayName("When the organization has no application, then return EMPTY with zero totals and every type at 0")
    void whenNoApplication_thenEmpty() {
        // Act
        LenderApprovedFundedCardDTO card = approvedFunded.getCard(7L, atlas, OCT_1, OCT_31);

        // Assert
        assertEquals(LenderApprovedFundedCardDTO.STATUS_EMPTY, card.status());
        assertEquals(0, card.approved().count());
        assertEquals(new BigDecimal("0.00"), card.approved().totalAmount());
        assertEquals(new BigDecimal("0.00"), card.funded().totalAmount());
        assertMix(card.fundedLoanMix(), new long[]{0, 0, 0, 0, 0},
                new String[]{"0.00", "0.00", "0.00", "0.00", "0.00"});
    }

    @Test
    @DisplayName("When the user is not a member, then return 404")
    void whenNotMember_thenNotFound() {
        // Arrange
        givenAtlasApplications();

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> approvedFunded.getCard(8L, atlas, OCT_1, OCT_31));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    @DisplayName("When the period is invalid, then throw IllegalArgumentException")
    void whenInvalidPeriod_thenThrow() {
        // Act & Assert
        IllegalArgumentException onlyOne = assertThrows(IllegalArgumentException.class,
                () -> approvedFunded.getCard(7L, atlas, OCT_1, null));
        assertEquals("fromDate and toDate must be given together", onlyOne.getMessage());
        IllegalArgumentException reversed = assertThrows(IllegalArgumentException.class,
                () -> approvedFunded.getCard(7L, atlas, OCT_31, OCT_1));
        assertEquals("fromDate must not be after toDate", reversed.getMessage());
    }

    @Test
    @DisplayName("When organizationId is not positive, then throw IllegalArgumentException")
    void whenOrganizationIdInvalid_thenThrow() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> approvedFunded.getCard(7L, 0L, OCT_1, OCT_31));
    }
}
