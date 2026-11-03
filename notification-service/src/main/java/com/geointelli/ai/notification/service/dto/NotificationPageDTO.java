package com.geointelli.ai.notification.service.dto;

import java.util.List;

/** Zero-based pagination of the authenticated user's inbox, newest creation time and ID first. */
public record NotificationPageDTO(List<NotificationDTO> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last) {}
