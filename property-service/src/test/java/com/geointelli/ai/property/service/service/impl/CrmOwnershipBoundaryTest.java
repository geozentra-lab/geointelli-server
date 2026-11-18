package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.mapper.CrmMapper;
import com.geointelli.ai.property.service.mapper.CrmWorkMapper;
import com.geointelli.ai.property.service.repository.CrmActivityRepository;
import com.geointelli.ai.property.service.repository.CrmClientRepository;
import com.geointelli.ai.property.service.repository.CrmLeadRepository;
import com.geointelli.ai.property.service.repository.CrmTaskRepository;
import com.geointelli.ai.property.service.repository.CrmTransactionRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.CrmActivityService;
import com.geointelli.ai.property.service.service.CrmValidationService;
import com.geointelli.ai.property.service.service.CrmWorkReferenceService;
import com.geointelli.ai.property.service.service.CurrentUserService;

/**
 * Business ownership checks still apply after the filter grants access to CRM
 * features.
 */
class CrmOwnershipBoundaryTest {
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final CrmClientRepository clients = mock(CrmClientRepository.class);
    private final CrmLeadRepository leads = mock(CrmLeadRepository.class);
    private final CrmTaskRepository tasks = mock(CrmTaskRepository.class);
    private final CrmTransactionRepository transactions = mock(CrmTransactionRepository.class);
    private final CrmValidationService validation = mock(CrmValidationService.class);
    private final CrmActivityService activities = mock(CrmActivityService.class);

    private void foreign(Runnable action) {
        User user = new User();
        user.setId(7L);
        when(current.getCurrentUserEntity()).thenReturn(user);
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void clientsAndLeadsRejectForeignReadsAndWritesBeforeAnyChanges() {
        var clientService = new CrmClientServiceImpl(clients, leads, tasks, transactions, current,
                validation, activities, mock(CrmMapper.class));
        var leadService = new CrmLeadServiceImpl(clients, leads, mock(PropertyRepository.class), current,
                validation, activities, mock(CrmMapper.class), new ObjectMapper());
        foreign(() -> clientService.get(99L));
        foreign(() -> clientService.update(99L, null));
        foreign(() -> clientService.delete(99L));
        foreign(() -> leadService.get(99L));
        foreign(() -> leadService.update(99L, null));
        verify(clients).findByIdAndUser_Id(99L, 7L);
        verify(clients, times(2)).findOwnedForUpdate(99L, 7L);
        verify(leads).findByIdAndUser_Id(99L, 7L);
        verify(leads).findOwnedForUpdate(99L, 7L);
        verifyNoMoreInteractions(clients, leads);
        verifyNoInteractions(activities, tasks, transactions);
    }

    @Test
    void tasksAndDealsRejectForeignReadsUpdatesAndHistory() {
        var references = mock(CrmWorkReferenceService.class);
        var history = mock(CrmActivityRepository.class);
        var mapper = mock(CrmWorkMapper.class);
        var taskService = new CrmTaskServiceImpl(tasks, current, validation, references, activities, history, mapper,
                Clock.systemUTC());
        var dealService = new CrmTransactionServiceImpl(transactions, current, validation, references, activities,
                history, mapper, Clock.systemUTC());
        foreign(() -> taskService.get(99L));
        foreign(() -> taskService.update(99L, null));
        foreign(() -> taskService.delete(99L));
        foreign(() -> taskService.history(99L, 0, 20));
        foreign(() -> dealService.get(99L));
        foreign(() -> dealService.update(99L, null));
        foreign(() -> dealService.history(99L, 0, 20));
        verify(tasks, times(2)).findByIdAndUserId(99L, 7L);
        verify(tasks, times(2)).findOwnedForUpdate(99L, 7L);
        verify(transactions, times(2)).findByIdAndUserId(99L, 7L);
        verify(transactions).findOwnedForUpdate(99L, 7L);
        verifyNoMoreInteractions(tasks, transactions);
        verifyNoInteractions(activities, history, references);
    }
}
