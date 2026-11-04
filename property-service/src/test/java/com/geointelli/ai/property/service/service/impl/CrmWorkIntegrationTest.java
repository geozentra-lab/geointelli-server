package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.CrmClientCreateRequest;
import com.geointelli.ai.property.service.dto.CrmLeadCreateRequest;
import com.geointelli.ai.property.service.dto.CrmLeadUpdateRequest;
import com.geointelli.ai.property.service.dto.CrmTaskCreateRequest;
import com.geointelli.ai.property.service.dto.CrmTaskUpdateRequest;
import com.geointelli.ai.property.service.dto.CrmTransactionCreateRequest;
import com.geointelli.ai.property.service.dto.CrmTransactionUpdateRequest;
import com.geointelli.ai.property.service.entity.CrmActivityType;
import com.geointelli.ai.property.service.entity.CrmTaskStatus;
import com.geointelli.ai.property.service.entity.CrmTransactionStage;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.mapper.CrmWorkMapper;
import com.geointelli.ai.property.service.service.CrmActivityService;
import com.geointelli.ai.property.service.service.CrmClientService;
import com.geointelli.ai.property.service.service.CrmLeadService;
import com.geointelli.ai.property.service.service.CrmTaskService;
import com.geointelli.ai.property.service.service.CrmTransactionService;
import com.geointelli.ai.property.service.service.CurrentUserService;
import jakarta.persistence.EntityManager;

/** Runs actual services, queries and migrations on H2 or an explicitly supplied disposable loopback PostgreSQL database. */
@DataJpaTest(showSql = false, properties = {
    "spring.datasource.url=${crm.work.test.url:jdbc:h2:mem:crm-work;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
    "spring.datasource.driver-class-name=${crm.work.test.driver:org.h2.Driver}",
    "spring.datasource.username=${crm.work.test.user:sa}", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({CrmTaskServiceImpl.class, CrmTransactionServiceImpl.class, CrmWorkReferenceServiceImpl.class,
    CrmWorkMapper.class, CrmClientServiceImpl.class, CrmLeadServiceImpl.class, CrmActivityServiceImpl.class,
    CrmValidationServiceImpl.class, CrmIntegrationTest.Beans.class, CrmWorkIntegrationTest.TimeConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrmWorkIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00.123456Z");
    @TestConfiguration
    static class TimeConfig {
        @Bean("crmWorkClock") Clock workClock() {
            return Clock.fixed(Instant.parse("2026-11-04T12:00:00.123456789Z"), ZoneOffset.UTC);
        }
    }

    @Autowired private CrmTaskService tasks;
    @Autowired private CrmTransactionService transactions;
    @Autowired private CrmClientService clients;
    @Autowired private CrmLeadService leads;
    @Autowired private CrmActivityService activity;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @MockBean private CurrentUserService currentUser;
    private Long clientId;
    private Long leadId;

    @BeforeAll
    void schema() throws Exception {
        createSchema(jdbc);
    }

    static void createSchema(JdbcTemplate jdbc) throws Exception {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            String url = connection.getMetaData().getURL();
            assertTrue(url.startsWith("jdbc:h2:mem:crm-work")
                || (url.startsWith("jdbc:postgresql://127.0.0.1:") && url.endsWith("/crm_work_test")),
                "Only the disposable CRM work database is allowed");
            jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE properties(id BIGINT PRIMARY KEY)");
            ClassPathResource base = new ClassPathResource("db/migration/V21__add_clients_leads_and_activity.sql");
            if (url.startsWith("jdbc:h2:")) {
                // H2 generates random CHECK names; give the fixture PostgreSQL's deterministic names.
                String sql = base.getContentAsString(StandardCharsets.UTF_8)
                    .replace("subject_type VARCHAR(20) NOT NULL CHECK", "subject_type VARCHAR(20) NOT NULL CONSTRAINT crm_activities_subject_type_check CHECK")
                    .replace("activity_type VARCHAR(40) NOT NULL CHECK", "activity_type VARCHAR(40) NOT NULL CONSTRAINT crm_activities_activity_type_check CHECK");
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
            } else {
                ScriptUtils.executeSqlScript(connection, base);
            }
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V23__add_crm_tasks_and_transactions.sql"));
        }
    }

    @BeforeEach
    void setup() {
        jdbc.update("INSERT INTO users VALUES(7),(8)");
        jdbc.update("INSERT INTO properties VALUES(100),(101)");
        asUser(7);
        clientId = clients.create(new CrmClientCreateRequest("Client", "client@example.com", null, null)).id();
        leadId = leads.create(new CrmLeadCreateRequest(clientId, 100L, "Enquiry", "MANUAL", null), "setup-lead").lead().id();
    }

    private void asUser(long id) {
        User user = new User();
        user.setId(id);
        when(currentUser.getCurrentUserEntity()).thenReturn(user);
    }

    private Long deal() {
        return transactions.create(new CrmTransactionCreateRequest(" Deal ", " Description ", clientId, leadId,
            100L, LocalDate.of(2026, 12, 1))).id();
    }

    private CrmTaskCreateRequest task(Instant due) {
        return new CrmTaskCreateRequest(" Call client ", " Follow up ", due, null, leadId, null, null);
    }

    private void hasStatus(HttpStatus status, Runnable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action::run).getStatusCode());
    }

    @Test
    void standaloneTasksAndOldCrmActivityRemainCompatible() {
        var task = tasks.create(new CrmTaskCreateRequest("Research", null, null, null, null, null, null));
        assertNull(task.clientId());
        assertFalse(task.overdue());
        assertEquals(CrmTaskStatus.OPEN, task.status());
        assertEquals(1, tasks.history(task.id(), 0, 20).totalElements());
        assertEquals(1, activity.clientHistory(clientId, 0, 20).content().stream()
            .filter(event -> event.activityType() == CrmActivityType.CLIENT_CREATED).count());
    }

    @Test
    void linkedTaskInheritsOwnedLeadAndPropertyAndRoundTripsMicroseconds() {
        var result = tasks.create(task(Instant.parse("2026-11-04T11:00:00.123456789Z")));
        entityManager.clear();
        result = tasks.get(result.id());
        assertEquals(clientId, result.clientId());
        assertEquals(100L, result.propertyId());
        assertEquals("Call client", result.title());
        assertTrue(result.overdue());
        assertEquals(123456000, result.dueAt().getNano());
        assertNotNull(result.createdAt());
    }

    @Test
    void completionReopeningAndNoOpUpdatesHaveCorrectTimesAndHistory() {
        Long id = tasks.create(task(NOW.minusSeconds(60))).id();
        CrmTaskUpdateRequest complete = new CrmTaskUpdateRequest();
        complete.setStatus(CrmTaskStatus.COMPLETED);
        var done = tasks.update(id, complete);
        assertEquals(NOW, done.completedAt());
        assertFalse(done.overdue());
        tasks.update(id, complete);
        assertEquals(NOW, tasks.get(id).completedAt());
        assertEquals(2, tasks.history(id, 0, 20).totalElements());
        CrmTaskUpdateRequest reopen = new CrmTaskUpdateRequest();
        reopen.setStatus(CrmTaskStatus.OPEN);
        var reopened = tasks.update(id, reopen);
        assertNull(reopened.completedAt());
        assertTrue(reopened.overdue());
        assertEquals(3, tasks.history(id, 0, 20).totalElements());
    }

    @Test
    void taskPatchClearsOptionalFieldsAndPreservesOmittedFields() {
        Long id = tasks.create(task(NOW)).id();
        CrmTaskUpdateRequest patch = new CrmTaskUpdateRequest();
        patch.setDueAt(null);
        patch.setDescription(null);
        var result = tasks.update(id, patch);
        assertNull(result.dueAt());
        assertNull(result.description());
        assertEquals("Call client", result.title());
        tasks.update(id, new CrmTaskUpdateRequest());
        assertEquals(2, tasks.history(id, 0, 20).totalElements());
    }

    @Test
    void summariesAreRealOwnedCountsAndRespectOverdueBoundary() {
        Long done = tasks.create(task(NOW.minusSeconds(60))).id();
        CrmTaskUpdateRequest complete = new CrmTaskUpdateRequest();
        complete.setStatus(CrmTaskStatus.COMPLETED);
        tasks.update(done, complete);
        tasks.create(task(NOW));
        tasks.create(task(NOW.minusSeconds(1)));
        var summary = tasks.summary();
        assertEquals(3, summary.total());
        assertEquals(2, summary.open());
        assertEquals(1, summary.completed());
        assertEquals(1, summary.overdue());
        assertEquals(NOW, summary.asOf());
        assertEquals(2, tasks.list(0, 1, CrmTaskStatus.OPEN).totalElements());
        asUser(8);
        assertEquals(0, tasks.summary().total());
        assertEquals(0, tasks.summary().overdue());
    }

    @Test
    void pipelineShowsEveryStageAndTracksOnlyActualTransitions() {
        Long id = deal();
        assertEquals(1, transactions.pipeline().stages().get(CrmTransactionStage.PROSPECT));
        assertEquals(6, transactions.pipeline().stages().size());
        for (var stage : List.of(CrmTransactionStage.VIEWING, CrmTransactionStage.OFFER,
                CrmTransactionStage.UNDER_CONTRACT, CrmTransactionStage.CLOSED)) {
            CrmTransactionUpdateRequest patch = new CrmTransactionUpdateRequest();
            patch.setStage(stage);
            transactions.update(id, patch);
        }
        entityManager.clear();
        assertEquals(NOW, transactions.get(id).closedAt());
        var pipeline = transactions.pipeline();
        assertEquals(1, pipeline.total());
        assertEquals(1, pipeline.stages().get(CrmTransactionStage.CLOSED));
        assertEquals(0, pipeline.stages().get(CrmTransactionStage.PROSPECT));
        assertEquals(5, transactions.history(id, 0, 20).totalElements());
        assertEquals(1, transactions.list(0, 20, CrmTransactionStage.CLOSED).totalElements());
    }

    @Test
    void unsupportedDealTransitionsAreConflictAndClosedTimestampIsStable() {
        Long id = deal();
        CrmTransactionUpdateRequest patch = new CrmTransactionUpdateRequest();
        patch.setStage(CrmTransactionStage.CLOSED);
        hasStatus(HttpStatus.CONFLICT, () -> transactions.update(id, patch));
        patch.setStage(CrmTransactionStage.CANCELLED);
        transactions.update(id, patch);
        transactions.update(id, patch);
        assertNull(transactions.get(id).closedAt());
        assertEquals(2, transactions.history(id, 0, 20).totalElements());
        patch.setStage(CrmTransactionStage.PROSPECT);
        hasStatus(HttpStatus.CONFLICT, () -> transactions.update(id, patch));
    }

    @Test
    void taskCompletionDoesNotAdvanceDealOrConvertLead() {
        Long transactionId = deal();
        Long taskId = tasks.create(new CrmTaskCreateRequest("Follow-up", null, null, null, null, null, transactionId)).id();
        CrmTaskUpdateRequest patch = new CrmTaskUpdateRequest();
        patch.setStatus(CrmTaskStatus.COMPLETED);
        tasks.update(taskId, patch);
        assertEquals(CrmTransactionStage.PROSPECT, transactions.get(transactionId).stage());
        assertEquals("NEW", leads.get(leadId).stage().name());
    }

    @Test
    void transactionSnapshotSurvivesLaterLeadEdits() {
        Long transactionId = deal();
        CrmLeadUpdateRequest patch = new CrmLeadUpdateRequest();
        patch.setPropertyId(101L);
        leads.update(leadId, patch);
        var followup = tasks.create(new CrmTaskCreateRequest("Follow-up", null, null, null, null, null, transactionId));
        assertEquals(100L, followup.propertyId());
        assertEquals(100L, transactions.get(transactionId).propertyId());
    }

    @Test
    void invalidOrCrossOwnerReferencesAreNeverAccepted() {
        Long other = clients.create(new CrmClientCreateRequest("Other", "other@example.com", null, null)).id();
        assertThrows(IllegalArgumentException.class, () -> tasks.create(
            new CrmTaskCreateRequest("Bad", null, null, other, leadId, null, null)));
        assertThrows(IllegalArgumentException.class, () -> transactions.create(
            new CrmTransactionCreateRequest("Bad", null, clientId, leadId, 101L, null)));
        hasStatus(HttpStatus.NOT_FOUND, () -> tasks.create(new CrmTaskCreateRequest("Bad", null, null, null, null, 999L, null)));
        Long transactionId = deal();
        asUser(8);
        hasStatus(HttpStatus.NOT_FOUND, () -> tasks.create(new CrmTaskCreateRequest("Bad", null, null, null, null, null, transactionId)));
        hasStatus(HttpStatus.NOT_FOUND, () -> transactions.create(new CrmTransactionCreateRequest("Bad", null, clientId, null, 100L, null)));
    }

    @Test
    void ownershipCoversReadsUpdatesDeletesHistoryAndCounts() {
        Long taskId = tasks.create(task(null)).id();
        Long transactionId = deal();
        asUser(8);
        hasStatus(HttpStatus.NOT_FOUND, () -> tasks.get(taskId));
        hasStatus(HttpStatus.NOT_FOUND, () -> tasks.update(taskId, new CrmTaskUpdateRequest()));
        hasStatus(HttpStatus.NOT_FOUND, () -> tasks.delete(taskId));
        hasStatus(HttpStatus.NOT_FOUND, () -> tasks.history(taskId, 0, 20));
        hasStatus(HttpStatus.NOT_FOUND, () -> transactions.get(transactionId));
        hasStatus(HttpStatus.NOT_FOUND, () -> transactions.update(transactionId, new CrmTransactionUpdateRequest()));
        hasStatus(HttpStatus.NOT_FOUND, () -> transactions.history(transactionId, 0, 20));
        assertEquals(0, tasks.list(0, 20, null).totalElements());
        assertEquals(0, transactions.list(0, 20, null).totalElements());
        assertEquals(0, transactions.pipeline().total());
    }

    @Test
    void deletingTaskPreservesDealClientPropertyAndHistory() {
        Long transactionId = deal();
        Long id = tasks.create(new CrmTaskCreateRequest("Follow-up", null, null, null, null, null, transactionId)).id();
        tasks.delete(id);
        hasStatus(HttpStatus.NOT_FOUND, () -> tasks.get(id));
        assertNotNull(transactions.get(transactionId));
        assertNotNull(clients.get(clientId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM properties", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_activities WHERE activity_type='TASK_DELETED'", Integer.class));
    }

    @Test
    void transactionPatchClearsPlanningDateAndDescriptionOnly() {
        Long id = deal();
        CrmTransactionUpdateRequest patch = new CrmTransactionUpdateRequest();
        patch.setExpectedCloseDate(null);
        patch.setDescription(null);
        var result = transactions.update(id, patch);
        assertNull(result.expectedCloseDate());
        assertNull(result.description());
        assertEquals("Deal", result.title());
        transactions.update(id, patch);
        assertEquals(2, transactions.history(id, 0, 20).totalElements());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1800-01-01T00:00:00Z", "2200-01-01T00:00:00Z"})
    void rejectsUnusableDueDates(String value) {
        assertThrows(IllegalArgumentException.class, () -> tasks.create(task(Instant.parse(value))));
    }

    @Test
    void nullStatusesAndMissingRequiredValuesAreBadRequests() {
        assertThrows(IllegalArgumentException.class, () -> tasks.create(new CrmTaskCreateRequest(" ", null, null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> transactions.create(new CrmTransactionCreateRequest("Bad", null, null, null, 100L, null)));
        Long id = tasks.create(task(null)).id();
        CrmTaskUpdateRequest patch = new CrmTaskUpdateRequest();
        patch.setStatus(null);
        assertThrows(IllegalArgumentException.class, () -> tasks.update(id, patch));
    }

    @Test
    void databaseRejectsCrossOwnerClientReferences() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO crm_tasks(user_id,client_id,title) VALUES(8,?,'Wrong owner')", clientId));
    }
}
