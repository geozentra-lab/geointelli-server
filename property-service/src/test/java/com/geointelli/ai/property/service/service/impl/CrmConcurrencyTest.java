package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.CrmClientCreateRequest;
import com.geointelli.ai.property.service.dto.CrmLeadCreateRequest;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.service.CrmActivityService;
import com.geointelli.ai.property.service.service.CrmClientService;
import com.geointelli.ai.property.service.service.CrmLeadService;
import com.geointelli.ai.property.service.service.CurrentUserService;

/** Uses separately committed service transactions to exercise race handling and rollback, not a shared test transaction. */
@DataJpaTest(showSql = false, properties = {
    "spring.datasource.url=jdbc:h2:mem:crm-concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
    "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({CrmClientServiceImpl.class, CrmLeadServiceImpl.class, CrmActivityServiceImpl.class,
    CrmValidationServiceImpl.class, CrmIntegrationTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrmConcurrencyTest {
    @Autowired private CrmClientService clients;
    @Autowired private CrmLeadService leads;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private CurrentUserService currentUser;
    @SpyBean private CrmActivityService activity;

    @BeforeAll
    void schema() throws Exception {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            CrmWorkIntegrationTest.createCrmTables(jdbc, connection);
        }
    }

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM crm_activities");
        jdbc.update("DELETE FROM crm_leads");
        jdbc.update("DELETE FROM crm_clients");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users VALUES(7)");
        User user = new User();
        user.setId(7L);
        when(currentUser.getCurrentUserEntity()).thenReturn(user);
    }

    private Long client(String email) {
        return clients.create(new CrmClientCreateRequest("Contact", email, null, null)).id();
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private <T> List<T> together(Callable<T> first, Callable<T> second) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var one = pool.submit(() -> { ready.countDown(); start.await(); return first.call(); });
            var two = pool.submit(() -> { ready.countDown(); start.await(); return second.call(); });
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private int attempt(Callable<?> operation) throws Exception {
        try {
            operation.call();
            return 200;
        } catch (ResponseStatusException error) {
            return error.getStatusCode().value();
        } catch (DataIntegrityViolationException error) {
            return 409;
        }
    }

    @Test
    void concurrentIdenticalSubmissionsCommitOneLeadAndOneCreationEvent() throws Exception {
        Long clientId = client("contact@example.com");
        CrmLeadCreateRequest request = new CrmLeadCreateRequest(clientId, null, "Enquiry", "MANUAL", null);
        var result = together(() -> leads.create(request, "same-key").created(),
            () -> leads.create(request, "same-key").created());
        assertEquals(1, result.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, count("crm_leads"));
        assertEquals(2, count("crm_activities"));
    }

    @Test
    void sameKeyForDifferentClientsCannotCreateTwoLeads() throws Exception {
        Long one = client("one@example.com");
        Long two = client("two@example.com");
        var result = together(
            () -> attempt(() -> leads.create(new CrmLeadCreateRequest(one, null, "One", "MANUAL", null), "same-key")),
            () -> attempt(() -> leads.create(new CrmLeadCreateRequest(two, null, "Two", "MANUAL", null), "same-key")));
        assertTrue(result.contains(200));
        assertTrue(result.contains(409));
        assertEquals(1, count("crm_leads"));
        assertEquals(3, count("crm_activities"));
    }

    @Test
    void concurrentDuplicateEmailsAreProtectedByDatabaseConstraint() throws Exception {
        var result = together(() -> attempt(() -> client("same@example.com")),
            () -> attempt(() -> client("SAME@example.com")));
        assertTrue(result.contains(200));
        assertTrue(result.contains(409));
        assertEquals(1, count("crm_clients"));
        assertEquals(1, count("crm_activities"));
    }

    @Test
    void deletingClientAndSubmittingLeadCannotLeaveAnOrphan() throws Exception {
        Long clientId = client("contact@example.com");
        var result = together(
            () -> attempt(() -> { clients.delete(clientId); return null; }),
            () -> attempt(() -> leads.create(new CrmLeadCreateRequest(clientId, null, "Enquiry", "MANUAL", null), "racing")));
        assertTrue(result.contains(200));
        assertTrue(result.contains(404) || result.contains(409));
        assertEquals(count("crm_clients"), count("crm_leads"));
    }

    @Test
    void failedActivityWriteRollsBackTheClientAsWell() {
        CrmActivityService target = AopTestUtils.getUltimateTargetObject(activity);
        doThrow(new IllegalStateException("Simulated history failure")).when(target)
            .record(anyLong(), anyLong(), any(), anyLong(), any(), anyString());
        assertThrows(IllegalStateException.class, () -> client("rollback@example.com"));
        assertEquals(0, count("crm_clients"));
        assertEquals(0, count("crm_activities"));
    }
}
