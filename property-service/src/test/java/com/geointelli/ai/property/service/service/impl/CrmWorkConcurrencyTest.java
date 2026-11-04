package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import com.geointelli.ai.property.service.dto.CrmTaskCreateRequest;
import com.geointelli.ai.property.service.dto.CrmTaskUpdateRequest;
import com.geointelli.ai.property.service.dto.CrmTransactionCreateRequest;
import com.geointelli.ai.property.service.dto.CrmTransactionUpdateRequest;
import com.geointelli.ai.property.service.entity.CrmTaskStatus;
import com.geointelli.ai.property.service.entity.CrmTransactionStage;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.mapper.CrmWorkMapper;
import com.geointelli.ai.property.service.service.CrmActivityService;
import com.geointelli.ai.property.service.service.CrmClientService;
import com.geointelli.ai.property.service.service.CrmTaskService;
import com.geointelli.ai.property.service.service.CrmTransactionService;
import com.geointelli.ai.property.service.service.CurrentUserService;

/** Separate service transactions verify that concurrent requests do not duplicate changes or orphan references. */
@DataJpaTest(showSql = false, properties = {
    "spring.datasource.url=jdbc:h2:mem:crm-work-concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
    "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({CrmTaskServiceImpl.class, CrmTransactionServiceImpl.class, CrmWorkReferenceServiceImpl.class,
    CrmWorkMapper.class, CrmClientServiceImpl.class, CrmActivityServiceImpl.class, CrmValidationServiceImpl.class,
    CrmIntegrationTest.Beans.class, CrmWorkIntegrationTest.TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrmWorkConcurrencyTest {
    @Autowired private CrmTaskService tasks;
    @Autowired private CrmTransactionService transactions;
    @Autowired private CrmClientService clients;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private CurrentUserService currentUser;
    @SpyBean private CrmActivityService activity;
    private Long clientId;

    @BeforeAll
    void schema() throws Exception { CrmWorkIntegrationTest.createSchema(jdbc); }

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM crm_activities");
        jdbc.update("DELETE FROM crm_tasks");
        jdbc.update("DELETE FROM crm_transactions");
        jdbc.update("DELETE FROM crm_clients");
        jdbc.update("DELETE FROM properties");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users VALUES(7)");
        jdbc.update("INSERT INTO properties VALUES(100)");
        User user = new User();
        user.setId(7L);
        when(currentUser.getCurrentUserEntity()).thenReturn(user);
        clientId = clients.create(new CrmClientCreateRequest("Client", "client@example.com", null, null)).id();
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
        try { operation.call(); return 200; }
        catch (ResponseStatusException error) { return error.getStatusCode().value(); }
        catch (DataIntegrityViolationException error) { return 409; }
    }

    @Test
    void simultaneousCompletionProducesOneChangeAndOneCompletionTime() throws Exception {
        Long id = tasks.create(new CrmTaskCreateRequest("Call", null, null, clientId, null, null, null)).id();
        CrmTaskUpdateRequest patch = new CrmTaskUpdateRequest();
        patch.setStatus(CrmTaskStatus.COMPLETED);
        var result = together(() -> tasks.update(id, patch), () -> tasks.update(id, patch));
        assertEquals(result.get(0).completedAt(), result.get(1).completedAt());
        assertEquals(2, tasks.history(id, 0, 20).totalElements());
        assertEquals(1, tasks.summary().completed());
    }

    @Test
    void simultaneousSameStageChangeProducesOneHistoryEntry() throws Exception {
        Long id = transactions.create(new CrmTransactionCreateRequest("Deal", null, clientId, null, 100L, null)).id();
        CrmTransactionUpdateRequest patch = new CrmTransactionUpdateRequest();
        patch.setStage(CrmTransactionStage.VIEWING);
        together(() -> transactions.update(id, patch), () -> transactions.update(id, patch));
        assertEquals(2, transactions.history(id, 0, 20).totalElements());
        assertEquals(1, transactions.pipeline().stages().get(CrmTransactionStage.VIEWING));
    }

    @Test
    void clientDeletionCannotOrphanNewTask() throws Exception {
        var result = together(() -> attempt(() -> { clients.delete(clientId); return null; }),
            () -> attempt(() -> tasks.create(new CrmTaskCreateRequest("Call", null, null, clientId, null, null, null))));
        assertTrue(result.contains(200));
        assertTrue(result.contains(404) || result.contains(409));
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM crm_clients", Long.class),
            jdbc.queryForObject("SELECT COUNT(*) FROM crm_tasks", Long.class));
    }

    @Test
    void activityFailureRollsBackTaskWrite() {
        CrmActivityService target = AopTestUtils.getUltimateTargetObject(activity);
        doThrow(new IllegalStateException("Simulated history failure")).when(target)
            .record(anyLong(), nullable(Long.class), any(), anyLong(), any(), anyString());
        assertThrows(IllegalStateException.class, () -> tasks.create(
            new CrmTaskCreateRequest("Call", null, null, null, null, null, null)));
        assertEquals(0, tasks.summary().total());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crm_activities", Long.class));
    }
}
