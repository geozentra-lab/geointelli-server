package com.geointelli.ai.notification.service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.geointelli.ai.notification.service.config.NotificationJsonConfig;
import com.geointelli.ai.notification.service.config.NotificationProperties;
import com.geointelli.ai.notification.service.service.impl.AlertEventDecoderServiceImpl;
import jakarta.validation.ValidatorFactory;
/** Wire-contract fixtures independent from property-service classes. */
public final class EventTestFixtures {
    public static byte[] body() {
        try(var input=EventTestFixtures.class.getResourceAsStream("/new-match-v1.json")) { return input.readAllBytes(); }
        catch(IOException error) { throw new IllegalStateException(error); }
    }
    public static String text() { return new String(body(),StandardCharsets.UTF_8); }
    public static AlertEventDecoderServiceImpl decoder(ValidatorFactory factory) {
        return new AlertEventDecoderServiceImpl(new NotificationJsonConfig().notificationEventMapper(),factory.getValidator(),new NotificationProperties());
    }
    private EventTestFixtures() {}
}
