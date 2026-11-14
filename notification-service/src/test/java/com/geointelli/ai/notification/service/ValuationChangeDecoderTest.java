package com.geointelli.ai.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import com.geointelli.ai.notification.service.config.NotificationJsonConfig;
import com.geointelli.ai.notification.service.exception.InvalidAlertEventException;
import tools.jackson.databind.node.ObjectNode;

class ValuationChangeDecoderTest {
    static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void close() {
        VALIDATION.close();
    }

    static String fixture() throws Exception {
        try (var input = ValuationChangeDecoderTest.class.getResourceAsStream("/valuation-changed-v1.json")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void acceptsFrozenLimitedEstimateAndMicrosecondTime() throws Exception {
        var result = EventTestFixtures.decoder(VALIDATION).decode(fixture().getBytes(StandardCharsets.UTF_8));
        assertEquals("VALUATION_CHANGED", result.envelope().eventType());
        assertNotNull(result.valuationPayload());
        assertNull(result.payload());
        assertEquals("limited", result.valuationPayload().newValuation().status());
        assertEquals(result.envelope().occurredAt(), result.valuationPayload().observationFinishedAt());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/payload/changePercent|-9",
            "/payload/changeAmount|-1",
            "/payload/minimumChangePercent|11",
            "/payload/configuredDirection|\"INCREASE\"",
            "/payload/newValuation/status|\"unsupported\"",
            "/payload/oldValuation/value|0",
            "/payload/newValuation/historyId|9",
            "/propertyId|301401",
            "/alertRuleId|null",
            "/payload/observationFinishedAt|\"2026-11-13T10:00:00.720653200Z\""
    }, delimiter = '|')
    void rejectsInconsistentOrUnsupportedEvidenceWithoutTolerance(String pointer, String replacement) throws Exception {
        var mapper = new NotificationJsonConfig().notificationEventMapper();
        var root = mapper.readTree(fixture());
        assertFalse(root.at(pointer).isMissingNode());
        int separator = pointer.lastIndexOf('/');
        ObjectNode parent = (ObjectNode) root.at(pointer.substring(0, separator));
        parent.set(pointer.substring(separator + 1), mapper.readTree(replacement));
        byte[] bytes = mapper.writeValueAsBytes(root);
        assertThrows(InvalidAlertEventException.class, () -> EventTestFixtures.decoder(VALIDATION).decode(bytes));
    }
}
