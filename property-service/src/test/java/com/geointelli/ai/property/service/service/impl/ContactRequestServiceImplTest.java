package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.geointelli.ai.property.service.config.ContactRequestProperties;
import com.geointelli.ai.property.service.dto.PropertyContactRequest;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.repository.ContactRequestOutboxRepository;
import com.geointelli.ai.property.service.service.ContactRequestEventPublisher;
import com.geointelli.ai.property.service.service.ContactRequestOutboxService;
import com.geointelli.ai.property.service.service.ContactRequestService;
import com.geointelli.ai.property.service.service.CrmLeadService;
import com.geointelli.ai.property.service.service.CurrentUserService;

/** Real PostgreSQL transactions verify requester/recipient ownership, CRM atomicity, locks and outbox recovery. */
@DataJpaTest(showSql=false, properties={
    "spring.datasource.url=${contact.test.url:jdbc:postgresql://127.0.0.1:55448/contacts_test}",
    "spring.datasource.username=notification_test", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false",
    "contact-requests.publisher-enabled=false", "logging.level.org.hibernate.SQL=OFF"
})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EnabledIfSystemProperty(named="contact.test.url", matches="jdbc:postgresql://127\\.0\\.0\\.1:.*?/contacts_test")
@Import({ContactRequestServiceImpl.class, CrmInboundContactServiceImpl.class, CrmActivityServiceImpl.class,
    CrmValidationServiceImpl.class, CrmLeadServiceImpl.class, ContactRequestOutboxServiceImpl.class,
    ContactRequestProperties.class, CrmIntegrationTest.Beans.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContactRequestServiceImplTest {
    @Autowired ContactRequestService contacts;
    @Autowired ContactRequestOutboxService publishing;
    @Autowired ContactRequestOutboxRepository outbox;
    @Autowired ContactRequestProperties settings;
    @Autowired CrmLeadService leads;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockBean CurrentUserService currentUser;
    @MockBean ContactRequestEventPublisher broker;

    @BeforeAll void schema() throws Exception {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            String url = connection.getMetaData().getURL();
            assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:") && url.endsWith("/contacts_test"));
            for (String table : List.of("contact_request_outbox","property_contact_requests","crm_activities",
                    "crm_tasks","crm_transactions","crm_leads","crm_clients","properties","users"))
                jdbc.execute("DROP TABLE IF EXISTS " + table);
            jdbc.execute("""
                CREATE TABLE users(id BIGINT PRIMARY KEY, keycloak_user_id VARCHAR(255) UNIQUE NOT NULL,
                    username VARCHAR(255), email VARCHAR(255), first_name VARCHAR(255), last_name VARCHAR(255),
                    persona VARCHAR(40), created_at TIMESTAMP NOT NULL DEFAULT NOW(), updated_at TIMESTAMP NOT NULL DEFAULT NOW())
                """);
            jdbc.execute("CREATE TABLE properties(id BIGINT PRIMARY KEY)");
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V21__add_clients_leads_and_activity.sql"));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V23__add_crm_tasks_and_transactions.sql"));
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V25__add_property_contact_requests.sql"));
        }
        json.registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @BeforeEach void setup() {
        for (String table : List.of("contact_request_outbox","property_contact_requests","crm_activities",
                "crm_tasks","crm_transactions","crm_leads","crm_clients","properties","users"))
            jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO users(id,keycloak_user_id,persona,first_name) VALUES "
            + "(7,'requester','USER','User'),(8,'professional','REALTOR','Realtor'),(9,'outsider','USER','Other')");
        jdbc.update("INSERT INTO properties VALUES(100)");
        User professional = new User(); professional.setId(8L);
        when(currentUser.getCurrentUserEntity()).thenReturn(professional);
        reset(broker);
        settings.setPublisherEnabled(false);
        settings.setMaxAttempts(10);
    }

    private PropertyContactRequest request() {
        return new PropertyContactRequest(100L,8L," Person "," PERSON@example.com ",null,
            " Arrange a viewing ",true,"property-contact-v1");
    }
    private PropertyContactRequest message(String message) {
        var r=request();
        return new PropertyContactRequest(r.propertyId(),r.professionalUserId(),r.contactName(),r.contactEmail(),null,message,true,r.consentVersion());
    }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class); }
    private void status(HttpStatus expected,Runnable action) {
        assertEquals(expected,assertThrows(ResponseStatusException.class,action::run).getStatusCode());
    }

    @Test void createsRecipientOwnedLeadConsentAndEventAtomically() throws Exception {
        var result=contacts.submit(7L,request(),"one");
        assertTrue(result.created());
        assertEquals("SUBMITTED",result.request().status());
        assertEquals("PENDING",result.request().deliveryStatus());
        assertEquals(8L,jdbc.queryForObject("SELECT user_id FROM crm_leads",Long.class));
        assertEquals(8L,jdbc.queryForObject("SELECT user_id FROM crm_clients",Long.class));
        assertEquals("person@example.com",jdbc.queryForObject("SELECT email FROM crm_clients",String.class));
        assertTrue(jdbc.queryForObject("SELECT consent FROM property_contact_requests",Boolean.class));
        assertEquals("SUBMITTED",jdbc.queryForObject("SELECT status FROM property_contact_requests",String.class));
        assertEquals(2,count("crm_activities"));
        var event=json.readTree(outbox.findByRequestId(result.request().id()).orElseThrow().getEventJson());
        assertEquals("professional",event.get("userId").asText());
        assertTrue(event.get("alertRuleId").isNull());
        assertEquals(100,event.get("payload").get("propertyId").asLong());
        assertEquals("person@example.com",event.get("payload").get("contactEmail").asText());
        verifyNoInteractions(broker);
    }

    @Test void sameKeyReplaysAndDifferentInputConflicts() {
        var first=contacts.submit(7L,request(),"same");
        var replay=contacts.submit(7L,request(),"same");
        assertFalse(replay.created());
        assertEquals(first.request(),replay.request());
        status(HttpStatus.CONFLICT,()->contacts.submit(7L,message("Different"),"same"));
        assertEquals(1,count("crm_leads")); assertEquals(1,count("contact_request_outbox"));
    }

    @Test void phoneOnlySubmissionsAreValidWithoutInferringClientIdentity() {
        var phone = new PropertyContactRequest(100L,8L,"Person",null,"+1 305 555 1111",null,true,"property-contact-v1");
        contacts.submit(7L,phone,"phone-one");
        contacts.submit(7L,phone,"phone-one");
        assertEquals(1,count("crm_clients"));
        contacts.submit(7L,phone,"phone-two");
        assertEquals(2,count("crm_clients"));
        assertEquals(2,count("crm_leads"));
    }

    @Test void duplicateKeysAreScopedToRequesterAndClientIsReusedWithoutOverwrite() {
        var first=contacts.submit(7L,request(),"same");
        jdbc.update("UPDATE crm_clients SET name='Existing contact',phone='1234567'");
        var second=contacts.submit(9L,request(),"same");
        assertNotEquals(first.request().id(),second.request().id());
        assertEquals(2,count("crm_leads")); assertEquals(1,count("crm_clients"));
        assertEquals("Existing contact",jdbc.queryForObject("SELECT name FROM crm_clients",String.class));
        assertEquals("1234567",jdbc.queryForObject("SELECT phone FROM crm_clients",String.class));
    }

    @Test void onlyParticipantsCanReadStatusAndOnlyProfessionalCanReadCrmLead() {
        var result=contacts.submit(7L,request(),"one").request();
        assertEquals(result,contacts.get(7L,result.id()));
        assertEquals(result,contacts.get(8L,result.id()));
        status(HttpStatus.NOT_FOUND,()->contacts.get(9L,result.id()));
        User requester=new User();requester.setId(7L);
        when(currentUser.getCurrentUserEntity()).thenReturn(requester);
        status(HttpStatus.NOT_FOUND,()->leads.get(result.leadId()));
    }

    @Test void missingPropertyAndNonProfessionalDoNotCreateAnything() {
        jdbc.update("DELETE FROM properties");
        status(HttpStatus.NOT_FOUND,()->contacts.submit(7L,request(),"one"));
        jdbc.update("INSERT INTO properties VALUES(100)");
        jdbc.update("UPDATE users SET persona='USER' WHERE id=8");
        status(HttpStatus.NOT_FOUND,()->contacts.submit(7L,request(),"one"));
        assertEquals(0,count("property_contact_requests")); assertEquals(0,count("crm_leads"));
    }

    @Test void requiresConsentCurrentVersionContactAndValidIdempotencyKey() {
        var r=request();
        assertThrows(IllegalArgumentException.class,()->contacts.submit(7L,
            new PropertyContactRequest(100L,8L,"Person","person@example.com",null,null,false,r.consentVersion()),"one"));
        assertThrows(IllegalArgumentException.class,()->contacts.submit(7L,
            new PropertyContactRequest(100L,8L,"Person",null,null,null,true,r.consentVersion()),"one"));
        assertThrows(IllegalArgumentException.class,()->contacts.submit(7L,
            new PropertyContactRequest(100L,8L,"Person","bad email",null,null,true,r.consentVersion()),"one"));
        assertThrows(IllegalArgumentException.class,()->contacts.submit(7L,
            new PropertyContactRequest(100L,8L,"Person","person@example.com",null,null,true,"old"),"one"));
        assertThrows(IllegalArgumentException.class,()->contacts.submit(7L,r,null));
        assertEquals(0,count("property_contact_requests"));
    }

    @Test void aLeadFailureRollsBackRequestClientAndActivities() {
        jdbc.execute("ALTER TABLE crm_leads ADD CONSTRAINT test_reject_contact CHECK (source <> 'PROPERTY_CONTACT_REQUEST')");
        try {
            assertThrows(RuntimeException.class,()->contacts.submit(7L,request(),"one"));
            assertEquals(0,count("property_contact_requests")); assertEquals(0,count("crm_clients"));
            assertEquals(0,count("crm_activities")); assertEquals(0,count("contact_request_outbox"));
        } finally { jdbc.execute("ALTER TABLE crm_leads DROP CONSTRAINT test_reject_contact"); }
        assertTrue(contacts.submit(7L,request(),"one").created());
    }

    @Test void directoryReturnsOnlyProfessionalDisplayData() {
        var directory=contacts.professionals(0,20);
        assertEquals(1,directory.totalElements());
        assertEquals(8L,directory.content().get(0).id());
        assertEquals("Realtor",directory.content().get(0).name());
        jdbc.update("UPDATE users SET first_name=NULL,last_name=NULL,username='private@example.com' WHERE id=8");
        assertEquals("Professional 8",contacts.professionals(0,20).content().get(0).name());
        assertThrows(IllegalArgumentException.class,()->contacts.professionals(0,101));
    }

    @Test void historicalRequestCanReplayAfterPropertyDeletionAndLeadEdits() {
        var first=contacts.submit(7L,request(),"one").request();
        jdbc.update("DELETE FROM properties");
        jdbc.update("UPDATE crm_leads SET title='Changed',stage='CONTACTED'");
        assertEquals(first,contacts.submit(7L,request(),"one").request());
        assertEquals(1,count("crm_leads"));
    }

    @Test void publisherRetriesFrozenEventAndRecordsOnlyBrokerConfirmation() throws Exception {
        var request=contacts.submit(7L,request(),"one").request();
        var row=outbox.findByRequestId(request.id()).orElseThrow();
        settings.setPublisherEnabled(true);
        doThrow(new IllegalStateException("offline")).doNothing().when(broker).publish(row.getEventId(),row.getEventJson());
        assertTrue(publishing.processNext());
        var failed=outbox.findById(row.getId()).orElseThrow();
        assertEquals("PENDING",failed.getStatus());assertEquals(1,failed.getAttempts());
        jdbc.update("UPDATE contact_request_outbox SET next_attempt_at=NOW()");
        assertTrue(publishing.processNext());
        assertEquals("SENT",contacts.get(7L,request.id()).deliveryStatus());
        assertFalse(publishing.processNext());
        verify(broker,times(2)).publish(row.getEventId(),row.getEventJson());
    }

    @Test void publisherDisabledLeavesEventPendingAndExhaustionIsVisible() throws Exception {
        var request=contacts.submit(7L,request(),"one").request();
        assertFalse(publishing.processNext()); verifyNoInteractions(broker);
        settings.setPublisherEnabled(true);settings.setMaxAttempts(1);
        doThrow(new IllegalStateException("offline")).when(broker).publish(anyString(),anyString());
        assertTrue(publishing.processNext());
        assertEquals("FAILED",contacts.get(7L,request.id()).deliveryStatus());
        assertFalse(publishing.processNext());
    }

    @Test void publishesExactPersistedBytesForIndependentConsumerContractCheck() throws Exception {
        contacts.submit(7L,request(),"contract");
        settings.setPublisherEnabled(true);
        doAnswer(call -> {
            Files.writeString(Path.of("target/contact-published-event.json"), call.getArgument(1, String.class),
                StandardCharsets.UTF_8);
            return null;
        }).when(broker).publish(anyString(),anyString());
        assertTrue(publishing.processNext());
        assertTrue(Files.size(Path.of("target/contact-published-event.json")) > 0);
    }

    private <T> List<T> together(Callable<T> a,Callable<T> b) throws Exception {
        var pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try {
            var one=pool.submit(()->{ready.countDown();start.await();return a.call();});
            var two=pool.submit(()->{ready.countDown();start.await();return b.call();});
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            return List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS));
        } finally {start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
    }
    @Test void concurrentSameSubmissionCreatesOneLeadAndOneEvent() throws Exception {
        var results=together(()->contacts.submit(7L,request(),"same"),()->contacts.submit(7L,request(),"same"));
        assertEquals(results.get(0).request().id(),results.get(1).request().id());
        assertNotEquals(results.get(0).created(),results.get(1).created());
        assertEquals(1,count("crm_clients"));assertEquals(1,count("crm_leads"));assertEquals(1,count("contact_request_outbox"));
    }
    @Test void concurrentPublishersClaimDifferentRows() throws Exception {
        contacts.submit(7L,request(),"one"); contacts.submit(9L,request(),"two");
        settings.setPublisherEnabled(true);
        var results=together(publishing::processNext,publishing::processNext);
        assertTrue(results.get(0));assertTrue(results.get(1));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM contact_request_outbox WHERE status='SENT'",Long.class));
        verify(broker,times(2)).publish(anyString(),anyString());
    }
}

