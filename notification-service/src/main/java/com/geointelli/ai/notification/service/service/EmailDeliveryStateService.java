package com.geointelli.ai.notification.service.service;

import com.geointelli.ai.notification.service.dto.EmailDispatchDTO;
import com.geointelli.ai.notification.service.dto.EmailSendResultDTO;

/** Commits dispatch state separately from the external provider call. */
public interface EmailDeliveryStateService {
    void recoverExpired();

    EmailDispatchDTO prepareNext();

    void finish(EmailDispatchDTO dispatch, EmailSendResultDTO result);
}
