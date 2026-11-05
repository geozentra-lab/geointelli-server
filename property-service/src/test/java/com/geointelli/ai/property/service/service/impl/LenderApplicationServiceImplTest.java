package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.*;
import com.geointelli.ai.property.service.entity.LenderApplicationStage;
import com.geointelli.ai.property.service.mapper.LenderApplicationMapper;
import com.geointelli.ai.property.service.service.LenderApplicationImportService;
import com.geointelli.ai.property.service.service.LenderApplicationService;
import com.geointelli.ai.property.service.service.LenderOrganizationService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** Executes the actual services, repository queries and V24; uses only a disposable test database. */
@DataJpaTest(showSql = false, properties = {
    "spring.datasource.url=${lender.test.url:jdbc:h2:mem:lender-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000}",
    "spring.datasource.driver-class-name=${lender.test.driver:org.h2.Driver}",
    "spring.datasource.username=${lender.test.user:sa}", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({LenderApplicationServiceImpl.class, LenderOrganizationServiceImpl.class,
    LenderApplicationImportServiceImpl.class, LenderApplicationValidationServiceImpl.class,
    LenderApplicationMapper.class, LenderApplicationServiceImplTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LenderApplicationServiceImplTest {
    @TestConfiguration
    static class Beans {
        @Bean(destroyMethod = "close") ValidatorFactory validatorFactory() { return Validation.buildDefaultValidatorFactory(); }
        @Bean Validator validator(ValidatorFactory factory) { return factory.getValidator(); }
    }
    @Autowired private LenderApplicationService applications;
    @Autowired private LenderApplicationImportService imports;
    @Autowired private LenderOrganizationService organizations;
    @Autowired private JdbcTemplate jdbc;
    private Long org;

    @BeforeAll
    void schema() throws Exception {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            String url = connection.getMetaData().getURL();
            assertTrue(url.startsWith("jdbc:h2:mem:lender-test") ||
                (url.startsWith("jdbc:postgresql://127.0.0.1:") && url.endsWith("/lender_test")),
                "Only a disposable loopback lender_test database is allowed");
            jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE properties(id BIGINT PRIMARY KEY)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V24__add_lender_applications.sql"));
        }
    }

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM lender_applications");
        jdbc.update("DELETE FROM lender_organization_members");
        jdbc.update("DELETE FROM lender_organizations");
        jdbc.update("DELETE FROM properties");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users VALUES(7),(8),(9)");
        jdbc.update("INSERT INTO properties VALUES(100)");
        org = organizations.create(7L, " Test lender ").id();
    }

    private LenderApplicationCreateRequest request(String amount) {
        return new LenderApplicationCreateRequest(" Applicant ", " PERSON@example.com ", 100L, new BigDecimal(amount), "usd", " Notes ");
    }
    private LenderApplicationDTO create() { return applications.create(7L, org, request("300000")); }
    private LenderApplicationUpdateRequest stage(LenderApplicationStage stage) {
        var patch = new LenderApplicationUpdateRequest();
        patch.setStage(stage);
        return patch;
    }
    private LenderApplicationDTO approve(Long id) {
        applications.update(7L, org, id, stage(LenderApplicationStage.SUBMITTED));
        applications.update(7L, org, id, stage(LenderApplicationStage.UNDER_REVIEW));
        var patch = stage(LenderApplicationStage.APPROVED);
        patch.setApprovedAmount(new BigDecimal("250000"));
        return applications.update(7L, org, id, patch);
    }
    private LenderApplicationImportRequest.Row row(String id, LenderApplicationStage stage) {
        return new LenderApplicationImportRequest.Row(id, "Applicant", null, null, new BigDecimal("300000"), "USD",
            stage, null, null, null, null, null);
    }
    private LenderApplicationImportResult batch(LenderApplicationImportRequest.Row... rows) {
        return imports.importApplications(7L, org, new LenderApplicationImportRequest(" partner ", List.of(rows)));
    }
    private void status(HttpStatus expected, Runnable operation) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, operation::run).getStatusCode());
    }

    @Test
    void manualCreationNormalizesAndReturnsPersistedDetails() {
        var value = create();
        assertEquals(LenderApplicationStage.DRAFT, value.stage());
        assertEquals("Applicant", value.applicantName());
        assertEquals("person@example.com", value.applicantEmail());
        assertEquals("USD", value.currency());
        assertEquals(new BigDecimal("300000.00"), value.requestedAmount());
        assertNull(value.source());
        assertNotNull(value.createdAt());
        assertEquals(value, applications.get(7L, org, value.id()));
    }

    @Test
    void explicitMembershipAllowsSharingAndRevocationRetainsRecords() {
        var value = create();
        status(HttpStatus.NOT_FOUND, () -> applications.get(8L, org, value.id()));
        organizations.addMember(7L, org, 8L);
        organizations.addMember(7L, org, 8L);
        assertEquals(List.of(7L, 8L), organizations.members(8L, org));
        assertEquals(value.id(), applications.get(8L, org, value.id()).id());
        status(HttpStatus.FORBIDDEN, () -> organizations.addMember(8L, org, 9L));
        organizations.removeMember(7L, org, 8L);
        status(HttpStatus.NOT_FOUND, () -> applications.list(8L, org, 0, 20, null));
        assertEquals(value.id(), applications.get(7L, org, value.id()).id());
        status(HttpStatus.CONFLICT, () -> organizations.removeMember(7L, org, 7L));
    }

    @Test
    void foreignOrganizationCannotReadWriteSummarizeOrImport() {
        Long otherOrg = organizations.create(8L, "Other").id();
        var value = create();
        status(HttpStatus.NOT_FOUND, () -> applications.get(8L, otherOrg, value.id()));
        status(HttpStatus.NOT_FOUND, () -> applications.get(8L, org, value.id()));
        status(HttpStatus.NOT_FOUND, () -> applications.update(8L, org, value.id(), stage(LenderApplicationStage.SUBMITTED)));
        status(HttpStatus.NOT_FOUND, () -> applications.create(8L, org, request("100")));
        status(HttpStatus.NOT_FOUND, () -> applications.pipeline(8L, org));
        status(HttpStatus.NOT_FOUND, () -> imports.importApplications(8L, org,
            new LenderApplicationImportRequest("PARTNER", List.of(row("one", LenderApplicationStage.DRAFT)))));
        assertEquals(1, organizations.list(7L).size());
        assertEquals(org, organizations.list(7L).get(0).id());
    }

    @Test
    void stageWorkflowRecordsMilestonesAndNoOpsPreserveAudit() {
        var approved = approve(create().id());
        assertNotNull(approved.approvedAt());
        assertEquals(0, approved.approvedAt().getNano() % 1000);
        assertEquals(approved, applications.update(7L, org, approved.id(), stage(LenderApplicationStage.APPROVED)));
        var patch = stage(LenderApplicationStage.FUNDED);
        patch.setFundedAmount(new BigDecimal("240000"));
        var funded = applications.update(7L, org, approved.id(), patch);
        assertFalse(funded.fundedAt().isBefore(funded.approvedAt()));
        assertEquals(funded.approvedAt(), approved.approvedAt());
        status(HttpStatus.CONFLICT, () -> applications.update(7L, org, funded.id(), stage(LenderApplicationStage.UNDER_REVIEW)));
        assertEquals(funded, applications.update(7L, org, funded.id(), patch));
    }

    @Test
    void invalidTransitionOrAmountsRollBackEntirePatch() {
        var draft = create();
        status(HttpStatus.CONFLICT, () -> applications.update(7L, org, draft.id(), stage(LenderApplicationStage.FUNDED)));
        applications.update(7L, org, draft.id(), stage(LenderApplicationStage.SUBMITTED));
        applications.update(7L, org, draft.id(), stage(LenderApplicationStage.UNDER_REVIEW));
        var patch = stage(LenderApplicationStage.APPROVED);
        patch.setApplicantName("Changed");
        patch.setApprovedAmount(new BigDecimal("300001"));
        assertThrows(IllegalArgumentException.class, () -> applications.update(7L, org, draft.id(), patch));
        var unchanged = applications.get(7L, org, draft.id());
        assertEquals("Applicant", unchanged.applicantName());
        assertEquals(LenderApplicationStage.UNDER_REVIEW, unchanged.stage());
        assertNull(unchanged.approvedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "10000000000000000"})
    void invalidMoneyNeverPersists(String amount) {
        assertThrows(IllegalArgumentException.class, () -> applications.create(7L, org, request(amount)));
        assertEquals(0, applications.pipeline(7L, org).total());
    }

    @Test
    void validationHandlesMissingEmailCurrencyAndProperty() {
        assertThrows(IllegalArgumentException.class, () -> applications.create(7L, org,
            new LenderApplicationCreateRequest(null, null, null, BigDecimal.ONE, "USD", null)));
        assertThrows(IllegalArgumentException.class, () -> applications.create(7L, org,
            new LenderApplicationCreateRequest("A", "broken email", null, BigDecimal.ONE, "USD", null)));
        assertThrows(IllegalArgumentException.class, () -> applications.create(7L, org,
            new LenderApplicationCreateRequest("A", null, null, BigDecimal.ONE, "EUR", null)));
        assertThrows(IllegalArgumentException.class, () -> applications.create(7L, org,
            new LenderApplicationCreateRequest("A", null, 999L, BigDecimal.ONE, "USD", null)));
    }

    @Test
    void patchesCanClearOptionalFieldsButNotRequiredFields() {
        var value = create();
        var patch = new LenderApplicationUpdateRequest();
        patch.setApplicantEmail(null);
        patch.setNotes(null);
        patch.setPropertyId(null);
        var cleared = applications.update(7L, org, value.id(), patch);
        assertNull(cleared.applicantEmail());
        assertNull(cleared.propertyId());
        assertNull(cleared.notes());
        assertEquals(value.requestedAmount(), cleared.requestedAmount());
        patch.setRequestedAmount(null);
        assertThrows(IllegalArgumentException.class, () -> applications.update(7L, org, value.id(), patch));
    }

    @Test
    void paginationAndStageFiltersAreBoundedAndTenantScoped() {
        var first = create();
        var second = create();
        applications.update(7L, org, second.id(), stage(LenderApplicationStage.SUBMITTED));
        var page = applications.list(7L, org, 0, 1, null);
        assertEquals(2, page.totalElements());
        assertEquals(second.id(), page.content().get(0).id());
        assertEquals(first.id(), applications.list(7L, org, 0, 20, LenderApplicationStage.DRAFT).content().get(0).id());
        assertTrue(applications.list(7L, org, 10, 20, null).content().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> applications.list(7L, org, -1, 20, null));
        assertThrows(IllegalArgumentException.class, () -> applications.list(7L, org, 0, 101, null));
    }

    @Test
    void pipelineSeparatesCurrentApprovalFundingAndWithdrawalAndHasEmptyZeros() {
        assertEquals(7, applications.pipeline(7L, org).stages().size());
        var approved = approve(create().id());
        var funded = approve(create().id());
        var patch = stage(LenderApplicationStage.FUNDED);
        patch.setFundedAmount(new BigDecimal("200000"));
        applications.update(7L, org, funded.id(), patch);
        var withdrawn = approve(create().id());
        applications.update(7L, org, withdrawn.id(), stage(LenderApplicationStage.WITHDRAWN));
        create();
        Long otherOrg = organizations.create(8L, "Other").id();
        applications.create(8L, otherOrg, request("900000"));
        var pipeline = applications.pipeline(7L, org);
        assertEquals(4, pipeline.total());
        assertEquals(2, pipeline.approvedCount());
        assertEquals(new BigDecimal("500000.00"), pipeline.approvedAmount());
        assertEquals(1, pipeline.fundedCount());
        assertEquals(new BigDecimal("200000.00"), pipeline.fundedAmount());
        assertEquals(1, pipeline.stages().get(LenderApplicationStage.WITHDRAWN).count());
    }

    @Test
    void importsRetryWithoutDuplicatesOrAuditChangesAndUpdateThroughSameStages() {
        var row = row("A", LenderApplicationStage.DRAFT);
        var first = batch(row);
        assertEquals(1, first.created());
        Long id = first.rows().get(0).applicationId();
        var initial = applications.get(7L, org, id);
        assertEquals(1, batch(row).unchanged());
        assertEquals(initial, applications.get(7L, org, id));
        assertEquals(1, batch(row("A", LenderApplicationStage.SUBMITTED)).updated());
        assertEquals(1, batch(row("A", LenderApplicationStage.FUNDED)).rejected());
        assertEquals(LenderApplicationStage.SUBMITTED, applications.get(7L, org, id).stage());
        assertEquals(1, applications.pipeline(7L, org).total());
    }

    @Test
    void importsCommitGoodRowsAndRejectBadRowsWithoutLeakingData() {
        var result = batch(row("A", LenderApplicationStage.DRAFT), row("bad", LenderApplicationStage.FUNDED),
            row("C", LenderApplicationStage.SUBMITTED));
        assertEquals(2, result.created());
        assertEquals(1, result.rejected());
        assertEquals("INVALID_ROW", result.rows().get(1).errorCode());
        assertNull(result.rows().get(1).applicationId());
        assertEquals(2, applications.pipeline(7L, org).total());
        assertEquals(2, batch(row("A", LenderApplicationStage.DRAFT), row("C", LenderApplicationStage.SUBMITTED)).unchanged());
    }

    @Test
    void sameSourceIdsAreIndependentAcrossOrganizationsAndSources() {
        batch(row("A", LenderApplicationStage.DRAFT));
        var other = organizations.create(8L, "Other").id();
        var result = imports.importApplications(8L, other,
            new LenderApplicationImportRequest("PARTNER", List.of(row("A", LenderApplicationStage.DRAFT))));
        assertEquals(1, result.created());
        assertEquals(1, imports.importApplications(7L, org,
            new LenderApplicationImportRequest("SECOND", List.of(row("A", LenderApplicationStage.DRAFT)))).created());
    }

    @Test
    void repeatedIdentityInsideBatchIsProcessedInOrder() {
        var result = batch(row("A", LenderApplicationStage.DRAFT), row("A", LenderApplicationStage.DRAFT),
            row("A", LenderApplicationStage.SUBMITTED));
        assertEquals(1, result.created());
        assertEquals(1, result.unchanged());
        assertEquals(1, result.updated());
        assertEquals(result.rows().get(0).applicationId(), result.rows().get(2).applicationId());
    }

    @Test
    void importedMilestonesSurviveRealDatabaseRoundTripAtMicrosecondPrecision() {
        Instant approvedAt = Instant.parse("2020-01-01T00:00:00.123456789Z");
        Instant fundedAt = Instant.parse("2020-01-02T00:00:00.987654321Z");
        var row = new LenderApplicationImportRequest.Row("F", "Applicant", null, 100L,
            new BigDecimal("300000"), "USD", LenderApplicationStage.FUNDED, new BigDecimal("250000"),
            new BigDecimal("240000"), approvedAt, fundedAt, null);
        var first = batch(row);
        var value = applications.get(7L, org, first.rows().get(0).applicationId());
        assertEquals(Instant.parse("2020-01-01T00:00:00.123456Z"), value.approvedAt());
        assertEquals(Instant.parse("2020-01-02T00:00:00.987654Z"), value.fundedAt());
        assertEquals(1, batch(row).unchanged());
        jdbc.update("DELETE FROM properties WHERE id=100");
        assertNull(applications.get(7L, org, value.id()).propertyId());
    }

    @Test
    void invalidImportDatesAndBoundsNeverCreateRows() {
        var row = new LenderApplicationImportRequest.Row("bad", "Applicant", null, null, BigDecimal.TEN, "USD",
            LenderApplicationStage.FUNDED, BigDecimal.TEN, BigDecimal.ONE,
            Instant.parse("2020-01-02T00:00:00Z"), Instant.parse("2020-01-01T00:00:00Z"), null);
        assertEquals(1, batch(row).rejected());
        assertThrows(IllegalArgumentException.class, () -> imports.importApplications(7L, org,
            new LenderApplicationImportRequest("PARTNER", List.of())));
        assertThrows(IllegalArgumentException.class, () -> imports.importApplications(7L, org,
            new LenderApplicationImportRequest("PARTNER", Collections.nCopies(101, row))));
        assertEquals(0, applications.pipeline(7L, org).total());
    }

    @Test
    void databaseRejectsBrokenAmountsEvenWhenBypassingService() {
        var value = create();
        assertThrows(DataIntegrityViolationException.class, () ->
            jdbc.update("UPDATE lender_applications SET requested_amount=-1 WHERE id=?", value.id()));
        assertThrows(DataIntegrityViolationException.class, () ->
            jdbc.update("UPDATE lender_applications SET stage='FUNDED' WHERE id=?", value.id()));
        assertThrows(DataIntegrityViolationException.class, () ->
            jdbc.update("UPDATE lender_applications SET source='PARTNER' WHERE id=?", value.id()));
    }

    private <T> List<T> together(Callable<T> first, Callable<T> second) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            var one = pool.submit(() -> { ready.countDown(); start.await(); return first.call(); });
            var two = pool.submit(() -> { ready.countDown(); start.await(); return second.call(); });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentImportsProduceExactlyOneApplication() throws Exception {
        var result = together(() -> batch(row("A", LenderApplicationStage.DRAFT)),
            () -> batch(row("A", LenderApplicationStage.DRAFT)));
        assertEquals(1, result.stream().mapToInt(LenderApplicationImportResult::created).sum());
        assertEquals(1, result.stream().mapToInt(LenderApplicationImportResult::unchanged).sum());
        assertEquals(1, applications.pipeline(7L, org).total());
    }

    @Test
    void concurrentSameStageApprovalKeepsOneMilestone() throws Exception {
        var value = create();
        applications.update(7L, org, value.id(), stage(LenderApplicationStage.SUBMITTED));
        applications.update(7L, org, value.id(), stage(LenderApplicationStage.UNDER_REVIEW));
        var patch = stage(LenderApplicationStage.APPROVED);
        patch.setApprovedAmount(new BigDecimal("250000"));
        var result = together(() -> applications.update(7L, org, value.id(), patch),
            () -> applications.update(7L, org, value.id(), patch));
        assertEquals(result.get(0).approvedAt(), result.get(1).approvedAt());
        assertEquals(result.get(0).updatedAt(), result.get(1).updatedAt());
    }
}

