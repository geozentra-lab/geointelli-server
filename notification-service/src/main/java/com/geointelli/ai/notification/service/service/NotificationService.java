package com.geointelli.ai.notification.service.service;

import com.geointelli.ai.notification.service.dto.NotificationDTO;
import com.geointelli.ai.notification.service.dto.NotificationPageDTO;
import com.geointelli.ai.notification.service.dto.NotificationReadRequest;

/** Lists and updates notifications belonging to the authenticated recipient. */
public interface NotificationService {
    /** Lists the current user's inbox. Page starts at zero and size must be between 1 and 100. */
    NotificationPageDTO list(int page, int size);

    /** Marks an owned item read without changing its first read time on repeated requests. */
    NotificationDTO markRead(Long id, NotificationReadRequest request);
}
