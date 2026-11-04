package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.CrmClientCreateRequest;
import com.geointelli.ai.property.service.dto.CrmClientUpdateRequest;
import com.geointelli.ai.property.service.dto.CrmLeadCreateRequest;
import com.geointelli.ai.property.service.dto.CrmLeadUpdateRequest;
import com.geointelli.ai.property.service.dto.CrmNoteRequest;
import com.geointelli.ai.property.service.entity.CrmActivityType;
import com.geointelli.ai.property.service.entity.LeadStage;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.mapper.CrmMapper;
import com.geointelli.ai.property.service.service.CrmActivityService;
import com.geointelli.ai.property.service.service.CrmClientService;
import com.geointelli.ai.property.service.service.CrmLeadService;
import com.geointelli.ai.property.service.service.CurrentUserService;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** Exercises the real CRM services, repositories, mapper and migration on an isolated H2 database. */
@DataJpaTest(showSql = false, properties = {
    "spring.datasource.url=jdbc:h2:mem:crm-integration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({CrmClientServiceImpl.class, CrmLeadServiceImpl.class, CrmActivityServiceImpl.class,
    CrmValidationServiceImpl.class, CrmIntegrationTest.Beans.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrmIntegrationTest {
    @TestConfiguration
    static class Beans {
        @Bean CrmMapper crmMapper() { return Mappers.getMapper(CrmMapper.class); }
        @Bean ObjectMapper crmJson() { return new ObjectMapper(); }
        @Bean(destroyMethod = "close") ValidatorFactory crmValidatorFactory() { return Validation.buildDefaultValidatorFactory(); }
        @Bean Validator crmValidator(ValidatorFactory factory) { return factory.getValidator(); }
    }

    @Autowired private CrmClientService clients;
    @Autowired private CrmLeadService leads;
    @Autowired private CrmActivityService activity;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @MockBean private CurrentUserService currentUser;

    @BeforeAll
    void schema() throws Exception {
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE properties(id BIGINT PRIMARY KEY)");
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V21__add_clients_leads_and_activity.sql"));
        }
    }

    @BeforeEach
    void setup() {
        jdbc.update("INSERT INTO users VALUES(7),(8)");
        jdbc.update("INSERT INTO properties VALUES(100)");
        asUser(7L);
    }

    private void asUser(long id) {
        User user = new User();
        user.setId(id);
        when(currentUser.getCurrentUserEntity()).thenReturn(user);
    }

    private Long client() {
        return clients.create(new CrmClientCreateRequest(" Alex ", " Alex@Example.com ", null, " test ")).id();
    }

    private CrmLeadCreateRequest submission(Long clientId) {
        return new CrmLeadCreateRequest(clientId, 100L, " Interested in property ", " WEBSITE ", null);
    }

    private void hasStatus(HttpStatus expected, Runnable action) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, action::run).getStatusCode());
    }

    @Test
    void clientsNormalizeAndPersistAuditTimestampsAcrossRoundTrip() {
        Long id = client();
        entityManager.clear();
        var result = clients.get(id);
        assertEquals("Alex", result.name());
        assertEquals("alex@example.com", result.email());
        assertEquals("test", result.notes());
        assertNotNull(result.createdAt());
        assertNotNull(result.updatedAt());
        assertEquals(1, clients.list(0, 20).totalElements());
        assertEquals(CrmActivityType.CLIENT_CREATED, activity.list(0, 20).content().get(0).activityType());
    }

    @Test
    void duplicateEmailIsOwnerScopedAndCaseInsensitive() {
        client();
        hasStatus(HttpStatus.CONFLICT, () -> clients.create(new CrmClientCreateRequest("Other", "ALEX@example.com", null, null)));
        asUser(8);
        assertNotNull(clients.create(new CrmClientCreateRequest("Other owner", "ALEX@example.com", null, null)).id());
        assertEquals(1, clients.list(0, 20).totalElements());
        assertEquals(1, activity.list(0, 20).totalElements());
    }

    @Test
    void phoneOnlyContactsCanShareANumberWithoutBeingMerged() {
        clients.create(new CrmClientCreateRequest("One", null, "+1 (305) 555-0100", null));
        clients.create(new CrmClientCreateRequest("Two", null, "+1 (305) 555-0100", null));
        assertEquals(2, clients.list(0, 20).totalElements());
    }

    @Test
    void patchKeepsOmittedFieldsClearsNotesAndDoesNotInventNoOpActivity() {
        Long id = client();
        clients.update(id, new CrmClientUpdateRequest());
        assertEquals(1, activity.list(0, 20).totalElements());
        CrmClientUpdateRequest patch = new CrmClientUpdateRequest();
        patch.setNotes(null);
        assertNull(clients.update(id, patch).notes());
        assertEquals("alex@example.com", clients.get(id).email());
        assertEquals(2, activity.list(0, 20).totalElements());
        clients.update(id, patch);
        assertEquals(2, activity.list(0, 20).totalElements());
    }

    @Test
    void patchCannotClearTheLastContactMethodOrName() {
        Long id = client();
        CrmClientUpdateRequest patch = new CrmClientUpdateRequest();
        patch.setEmail(null);
        assertThrows(IllegalArgumentException.class, () -> clients.update(id, patch));
    }

    @Test
    void clientDeleteKeepsHistoricalEventsAndDoesNotDeleteProperty() {
        Long id = client();
        clients.delete(id);
        hasStatus(HttpStatus.NOT_FOUND, () -> clients.get(id));
        assertEquals(0, clients.list(0, 20).totalElements());
        assertEquals(2, activity.list(0, 20).totalElements());
        assertEquals(CrmActivityType.CLIENT_DELETED, activity.list(0, 20).content().get(0).activityType());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM properties", Integer.class));
    }

    @Test
    void submissionReplayCreatesExactlyOneLeadAndOneCreationEventEvenAfterEdits() {
        Long clientId = client();
        var first = leads.create(submission(clientId), "submission-1");
        assertTrue(first.created());
        CrmLeadUpdateRequest patch = new CrmLeadUpdateRequest();
        patch.setStage(LeadStage.CONTACTED);
        leads.update(first.lead().id(), patch);
        entityManager.clear();
        var replay = leads.create(submission(clientId), "submission-1");
        assertFalse(replay.created());
        assertEquals(first.lead().id(), replay.lead().id());
        assertEquals(LeadStage.CONTACTED, replay.lead().stage());
        assertEquals(1, leads.list(0, 20, null).totalElements());
        assertEquals(2, activity.leadHistory(first.lead().id(), 0, 20).totalElements());
    }

    @Test
    void sameKeyWithDifferentPayloadReturnsConflictAndDoesNotCreateLead() {
        Long id = client();
        leads.create(submission(id), "same-key");
        hasStatus(HttpStatus.CONFLICT, () -> leads.create(new CrmLeadCreateRequest(id, null, "Different", "MANUAL", null), "same-key"));
        assertEquals(1, leads.list(0, 20, null).totalElements());
    }

    @Test
    void newKeyMeansSeparateEnquiryAndKeysAreIndependentBetweenOwners() {
        Long id = client();
        leads.create(submission(id), "same-key");
        leads.create(submission(id), "new-key");
        asUser(8);
        Long otherClient = client();
        leads.create(submission(otherClient), "same-key");
        assertEquals(1, leads.list(0, 20, null).totalElements());
    }

    @Test
    void forwardStagesAndLostAreSupportedButTerminalLeadsCannotReopen() {
        Long id = leads.create(submission(client()), "stages").lead().id();
        for (LeadStage stage : List.of(LeadStage.CONTACTED, LeadStage.QUALIFIED, LeadStage.CONVERTED)) {
            CrmLeadUpdateRequest patch = new CrmLeadUpdateRequest();
            patch.setStage(stage);
            assertEquals(stage, leads.update(id, patch).stage());
        }
        CrmLeadUpdateRequest reopen = new CrmLeadUpdateRequest();
        reopen.setStage(LeadStage.NEW);
        hasStatus(HttpStatus.CONFLICT, () -> leads.update(id, reopen));
        assertEquals(LeadStage.CONVERTED, leads.get(id).stage());
        assertEquals(1, leads.list(0, 20, LeadStage.CONVERTED).totalElements());
        assertEquals(0, leads.list(0, 20, LeadStage.NEW).totalElements());
    }

    @Test
    void stageCannotSkipAheadAndSameStageDoesNotAddActivity() {
        Long id = leads.create(submission(client()), "stages").lead().id();
        CrmLeadUpdateRequest patch = new CrmLeadUpdateRequest();
        patch.setStage(LeadStage.NEW);
        leads.update(id, patch);
        assertEquals(1, activity.leadHistory(id, 0, 20).totalElements());
        patch.setStage(LeadStage.QUALIFIED);
        hasStatus(HttpStatus.CONFLICT, () -> leads.update(id, patch));
    }

    @Test
    void clientWithAnyLeadCannotBeDeleted() {
        Long clientId = client();
        leads.create(submission(clientId), "keep-contact");
        hasStatus(HttpStatus.CONFLICT, () -> clients.delete(clientId));
        assertNotNull(clients.get(clientId));
    }

    @Test
    void clientHistoryIncludesLeadChangesAndUserNotesWithStablePagination() {
        Long clientId = client();
        Long leadId = leads.create(submission(clientId), "history").lead().id();
        var note = activity.addLeadNote(leadId, new CrmNoteRequest(" Called client "));
        assertEquals("Called client", note.description());
        assertEquals(0, note.createdAt().getNano() % 1000);
        var first = activity.clientHistory(clientId, 0, 2);
        var second = activity.clientHistory(clientId, 1, 2);
        assertEquals(3, first.totalElements());
        assertEquals(2, first.content().size());
        assertEquals(1, second.content().size());
        assertTrue(first.content().get(1).id() > second.content().get(0).id());
        assertEquals(2, activity.leadHistory(leadId, 0, 20).totalElements());
        activity.addClientNote(clientId, new CrmNoteRequest("Client note"));
        assertEquals(4, activity.clientHistory(clientId, 0, 20).totalElements());
    }

    @Test
    void ownershipAppliesToDetailsEditsHistoryNotesAndReferences() {
        Long clientId = client();
        Long leadId = leads.create(submission(clientId), "owned").lead().id();
        asUser(8);
        hasStatus(HttpStatus.NOT_FOUND, () -> clients.get(clientId));
        hasStatus(HttpStatus.NOT_FOUND, () -> clients.update(clientId, new CrmClientUpdateRequest()));
        hasStatus(HttpStatus.NOT_FOUND, () -> clients.delete(clientId));
        hasStatus(HttpStatus.NOT_FOUND, () -> leads.get(leadId));
        hasStatus(HttpStatus.NOT_FOUND, () -> leads.update(leadId, new CrmLeadUpdateRequest()));
        hasStatus(HttpStatus.NOT_FOUND, () -> leads.create(submission(clientId), "foreign"));
        hasStatus(HttpStatus.NOT_FOUND, () -> activity.clientHistory(clientId, 0, 20));
        hasStatus(HttpStatus.NOT_FOUND, () -> activity.leadHistory(leadId, 0, 20));
        hasStatus(HttpStatus.NOT_FOUND, () -> activity.addClientNote(clientId, new CrmNoteRequest("No")));
        hasStatus(HttpStatus.NOT_FOUND, () -> activity.addLeadNote(leadId, new CrmNoteRequest("No")));
        assertEquals(0, clients.list(0, 20).totalElements());
        assertEquals(0, leads.list(0, 20, null).totalElements());
        assertEquals(0, activity.list(0, 20).totalElements());
    }

    @Test
    void propertyReferencesAreValidatedAndCanBeCleared() {
        Long clientId = client();
        hasStatus(HttpStatus.NOT_FOUND, () -> leads.create(new CrmLeadCreateRequest(clientId, 999L, "Unknown", "MANUAL", null), "missing-property"));
        Long leadId = leads.create(submission(clientId), "clear-property").lead().id();
        CrmLeadUpdateRequest patch = new CrmLeadUpdateRequest();
        patch.setPropertyId(null);
        assertNull(leads.update(leadId, patch).propertyId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "key with spaces", "key/invalid"})
    void invalidSubmissionKeysAreRejected(String key) {
        Long id = client();
        assertThrows(IllegalArgumentException.class, () -> leads.create(submission(id), key));
    }

    @Test
    void invalidContactsAndNotesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> clients.create(new CrmClientCreateRequest("Name", null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> clients.create(new CrmClientCreateRequest("Name", "bad-email", null, null)));
        assertThrows(IllegalArgumentException.class, () -> clients.create(new CrmClientCreateRequest("Name", null, "1------", null)));
        Long id = client();
        assertThrows(IllegalArgumentException.class, () -> activity.addClientNote(id, new CrmNoteRequest(" ")));
    }

    @Test
    void databaseRejectsCrossOwnerLeadEvenIfServiceIsBypassed() {
        Long id = client();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
            INSERT INTO crm_leads(user_id,client_id,title,source,request_key,request_hash)
            VALUES(8,?,'Cross owner','MANUAL','bypass','hash')
            """, id));
    }
}
