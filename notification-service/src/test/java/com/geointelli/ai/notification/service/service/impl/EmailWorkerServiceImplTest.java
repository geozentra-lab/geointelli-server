package com.geointelli.ai.notification.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import com.geointelli.ai.notification.service.config.NotificationEmailProperties;
import com.geointelli.ai.notification.service.dto.EmailDispatchDTO;
import com.geointelli.ai.notification.service.dto.EmailSendResultDTO;
import com.geointelli.ai.notification.service.dto.EmailSendResultDTO.Outcome;
import com.geointelli.ai.notification.service.service.EmailDeliveryStateService;
import com.geointelli.ai.notification.service.service.EmailProviderService;

class EmailWorkerServiceImplTest {
    private final EmailDeliveryStateService state = mock(EmailDeliveryStateService.class);
    private final EmailProviderService provider = mock(EmailProviderService.class);
    private final NotificationEmailProperties settings = new NotificationEmailProperties();
    private final EmailWorkerServiceImpl worker = new EmailWorkerServiceImpl(state, provider, settings);
    private final EmailDispatchDTO task = new EmailDispatchDTO(1, 1, "owner@example.com", "Alert", "Body");

    @Test
    void disabledWorkerNeverTouchesTheProviderOrDatabase() {
        assertFalse(worker.processNext());
        verifyNoInteractions(state, provider);
    }

    @Test
    void noDueWorkDoesNotCallProvider() {
        settings.setEnabled(true);
        assertFalse(worker.processNext());
        verifyNoInteractions(provider);
    }

    @Test
    void commitsPreparationBeforeTheExternalCallAndFinishesAfterIt() {
        settings.setEnabled(true);
        when(state.prepareNext()).thenReturn(task);
        var result = new EmailSendResultDTO(Outcome.ACCEPTED, "ses-id", null);
        when(provider.send(task)).thenReturn(result);
        assertTrue(worker.processNext());
        var order = inOrder(state, provider);
        order.verify(state).recoverExpired();
        order.verify(state).prepareNext();
        order.verify(provider).send(task);
        order.verify(state).finish(task, result);
    }

    @Test
    void unexpectedProviderErrorsBecomeUnknownInsteadOfAnAutomaticRetry() {
        settings.setEnabled(true);
        when(state.prepareNext()).thenReturn(task);
        when(provider.send(task)).thenThrow(new IllegalStateException("unavailable"));
        assertTrue(worker.processNext());
        verify(state).finish(task, new EmailSendResultDTO(Outcome.UNKNOWN, null, "PROVIDER_OUTCOME_UNKNOWN"));
    }
}
