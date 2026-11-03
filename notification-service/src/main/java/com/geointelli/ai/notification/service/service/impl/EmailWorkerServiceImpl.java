package com.geointelli.ai.notification.service.service.impl;

import org.springframework.stereotype.Service;
import com.geointelli.ai.notification.service.config.NotificationEmailProperties;
import com.geointelli.ai.notification.service.dto.EmailSendResultDTO;
import com.geointelli.ai.notification.service.dto.EmailSendResultDTO.Outcome;
import com.geointelli.ai.notification.service.service.EmailDeliveryStateService;
import com.geointelli.ai.notification.service.service.EmailProviderService;
import com.geointelli.ai.notification.service.service.EmailWorkerService;

/**
 * Provider calls happen after SENDING commits, preventing blind resend
 * following a process crash.
 */
@Service
public class EmailWorkerServiceImpl implements EmailWorkerService {
    private final EmailDeliveryStateService state;
    private final EmailProviderService provider;
    private final NotificationEmailProperties settings;

    public EmailWorkerServiceImpl(EmailDeliveryStateService state, EmailProviderService provider,
            NotificationEmailProperties settings) {
        this.state = state;
        this.provider = provider;
        this.settings = settings;
    }

    @Override
    public boolean processNext() {
        if (!settings.isEnabled())
            return false;
        state.recoverExpired();
        var task = state.prepareNext();
        if (task == null)
            return false;
        EmailSendResultDTO result;
        try {
            result = provider.send(task);
        } catch (RuntimeException uncertain) {
            result = new EmailSendResultDTO(Outcome.UNKNOWN, null, "PROVIDER_OUTCOME_UNKNOWN");
        }
        state.finish(task, result);
        return true;
    }
}
