package com.geointelli.ai.notification.service.service;

import com.geointelli.ai.notification.service.dto.EmailDispatchDTO;
import com.geointelli.ai.notification.service.dto.EmailSendResultDTO;

/** Makes one provider attempt; the application owns retry decisions. */
public interface EmailProviderService {
    EmailSendResultDTO send(EmailDispatchDTO dispatch);
}
