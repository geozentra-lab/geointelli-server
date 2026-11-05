package com.geointelli.ai.property.service.dto;

import java.util.List;

/** Each row commits independently; rejected rows expose a safe code, never database details. */
public record LenderApplicationImportResult(int created, int updated, int unchanged, int rejected, List<RowResult> rows) {
    public record RowResult(int row, String status, Long applicationId, String errorCode) {}
}

