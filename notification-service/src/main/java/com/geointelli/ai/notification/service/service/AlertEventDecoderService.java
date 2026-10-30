package com.geointelli.ai.notification.service.service;

import com.geointelli.ai.notification.service.dto.DecodedAlertEventDTO;

/** Validates the envelope and payload before storage. */
public interface AlertEventDecoderService {
    DecodedAlertEventDTO decode(byte[] body);
}
