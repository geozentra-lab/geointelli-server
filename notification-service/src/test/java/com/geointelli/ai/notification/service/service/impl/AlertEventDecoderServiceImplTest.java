package com.geointelli.ai.notification.service.service.impl;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.geointelli.ai.notification.service.EventTestFixtures;
import com.geointelli.ai.notification.service.config.NotificationJsonConfig;
import com.geointelli.ai.notification.service.exception.InvalidAlertEventException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.node.ObjectNode;
class AlertEventDecoderServiceImplTest {
    static final ValidatorFactory FACTORY=Validation.buildDefaultValidatorFactory();
    final AlertEventDecoderServiceImpl service=EventTestFixtures.decoder(FACTORY);
    @AfterAll static void close() { FACTORY.close(); }
    @Test void acceptsProducerContract() {
        var decoded=service.decode(EventTestFixtures.body());
        assertEquals("property-alert-1",decoded.envelope().eventId());
        assertEquals(2,decoded.payload().newMatchCount().longValue()); assertEquals(64,decoded.fingerprint().length());
    }
    @Test void canonicalFingerprintIgnoresObjectKeyOrder() {
        var json=new NotificationJsonConfig().notificationEventMapper();var tree=json.readTree(EventTestFixtures.body());
        var reversed=json.createObjectNode();
        tree.properties().stream().sorted((a,b)->b.getKey().compareTo(a.getKey())).forEach(e->reversed.set(e.getKey(),e.getValue()));
        assertEquals(service.decode(EventTestFixtures.body()).fingerprint(),service.decode(json.writeValueAsBytes(reversed)).fingerprint());
    }
    @Test void changingRecipientChangesFingerprint() {
        assertNotEquals(service.decode(EventTestFixtures.body()).fingerprint(),service.decode(EventTestFixtures.text()
            .replace("test-keycloak-user","other-user").getBytes(StandardCharsets.UTF_8)).fingerprint());
    }
    @Test void additiveFieldsAreCompatible() {
        String body=EventTestFixtures.text().replace("\"eventVersion\": 1","\"futureMetadata\": true, \"eventVersion\": 1");
        assertDoesNotThrow(()->service.decode(body.getBytes(StandardCharsets.UTF_8)));
    }
    @ParameterizedTest @ValueSource(strings={"","{}","not json","null","[]"})
    void malformedMessagesAreRejected(String text) { assertThrows(InvalidAlertEventException.class,()->service.decode(text.getBytes(StandardCharsets.UTF_8))); }
    @Test void oversizedMessagesAreRejected() { assertThrows(InvalidAlertEventException.class,()->service.decode(new byte[262145])); }
    @Test void unsupportedVersionIsRejected() { reject("\"eventVersion\": 1","\"eventVersion\": 2"); }
    @Test void unsupportedTypeIsRejected() { reject("NEW_MATCH","VALUATION_CHANGED"); }
    @Test void nullSavedSearchIsRejected() { reject("\"savedSearchId\": 10","\"savedSearchId\": null"); }
    @Test void inconsistentTruncationIsRejected() { reject("\"previewTruncated\": false","\"previewTruncated\": true"); }
    @Test void countLessThanPreviewIsRejected() { reject("\"newMatchCount\": 2","\"newMatchCount\": 1"); }
    @Test void numericStringsAreRejected() { reject("\"eventVersion\": 1","\"eventVersion\": \"1\""); }
    @Test void fractionalIdsAreRejected() { reject("\"alertRuleId\": 3","\"alertRuleId\": 3.9"); }
    @Test void reversedObservationWindowIsRejected() { reject("2026-10-29T09:59:00Z","2026-10-29T10:01:00Z"); }
    @Test void duplicatePreviewIdsAreRejected() {
        var json=new NotificationJsonConfig().notificationEventMapper();var tree=json.readTree(EventTestFixtures.body());
        ((ObjectNode)tree.get("payload")).putArray("propertyIds").add(1).add(1);
        assertThrows(InvalidAlertEventException.class,()->service.decode(json.writeValueAsBytes(tree)));
    }
    @Test void trailingJsonIsRejected() {
        assertThrows(InvalidAlertEventException.class,()->service.decode((EventTestFixtures.text()+" {}").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void duplicateEnvelopeKeysAreRejected() { reject("\"eventVersion\": 1","\"eventVersion\": 1, \"eventVersion\": 2"); }
    @Test void excessivelyDeepAdditiveFieldsAreRejected() {
        String nested="[".repeat(60)+"0"+"]".repeat(60);
        String body=EventTestFixtures.text().replace("\"eventVersion\": 1","\"extra\": "+nested+", \"eventVersion\": 1");
        assertThrows(InvalidAlertEventException.class,()->service.decode(body.getBytes(StandardCharsets.UTF_8)));
    }
    void reject(String from,String to) {
        String body=EventTestFixtures.text();assertTrue(body.contains(from));
        assertThrows(InvalidAlertEventException.class,()->service.decode(body.replace(from,to).getBytes(StandardCharsets.UTF_8)));
    }
}
