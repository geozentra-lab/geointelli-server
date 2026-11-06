package com.geointelli.ai.notification.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import com.geointelli.ai.notification.service.EventTestFixtures;
import com.geointelli.ai.notification.service.exception.InvalidAlertEventException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/** Contact events cannot masquerade as saved-search alerts or redirect property references. */
class ContactRequestDecoderTest {
    static final ValidatorFactory FACTORY=Validation.buildDefaultValidatorFactory();
    final AlertEventDecoderServiceImpl decoder=EventTestFixtures.decoder(FACTORY);
    @AfterAll static void close() { FACTORY.close(); }
    private String fixture() throws IOException {
        try (var stream=getClass().getResourceAsStream("/contact-request-v1.json")) {
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private void reject(String from,String to) throws IOException {
        assertTrue(fixture().contains(from));
        assertThrows(InvalidAlertEventException.class,()->decoder.decode(fixture().replaceFirst(Pattern.quote(from),Matcher.quoteReplacement(to)).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void acceptsIndependentContactContract() throws Exception {
        var event=decoder.decode(fixture().getBytes(StandardCharsets.UTF_8));
        assertNull(event.payload()); assertNotNull(event.contactPayload());
        assertNull(event.envelope().alertRuleId()); assertEquals(2L,event.contactPayload().leadId());
    }
    @Test void contactCannotCarryAnAlertRule() throws Exception { reject("\"alertRuleId\": null","\"alertRuleId\": 3"); }
    @Test void contactCannotCarrySavedSearch() throws Exception { reject("\"savedSearchId\": null","\"savedSearchId\": 10"); }
    @Test void cannotRedirectProperty() throws Exception { reject("\"propertyId\": 100","\"propertyId\": 200"); }
    @Test void eventIdentityMustMatchRequest() throws Exception { reject("property-contact-1","property-contact-2"); }
    @Test void sourceChangeMustMatchRequest() throws Exception { reject("contact-request:1","contact-request:2"); }
    @Test void contactRequiresPositiveLead() throws Exception { reject("\"leadId\": 2","\"leadId\": 0"); }
    @Test void contactRequiresContactDetails() throws Exception { reject("\"contactEmail\": \"person@example.com\"","\"contactEmail\": null"); }
    @Test void newMatchStillRequiresRule() {
        assertThrows(InvalidAlertEventException.class,()->decoder.decode(EventTestFixtures.text()
            .replace("\"alertRuleId\": 3","\"alertRuleId\": null").getBytes(StandardCharsets.UTF_8)));
    }
}
