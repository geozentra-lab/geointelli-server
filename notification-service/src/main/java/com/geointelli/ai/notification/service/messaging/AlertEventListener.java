package com.geointelli.ai.notification.service.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import com.geointelli.ai.notification.service.service.AlertEventDecoderService;
import com.geointelli.ai.notification.service.service.AlertEventConsumptionService;
/** Handles raw bytes without trusting producer Java type headers. AUTO acknowledgement follows transaction commit. */
@Component
public class AlertEventListener {
    private final AlertEventDecoderService decoder;
    private final AlertEventConsumptionService consumption;
    public AlertEventListener(AlertEventDecoderService decoder,AlertEventConsumptionService consumption) {
        this.decoder=decoder; this.consumption=consumption;
    }
    @RabbitListener(id="property-alert-events",queues="${notifications.queue:geozentra.notifications.alerts}",
        containerFactory="notificationListenerFactory")
    public void onMessage(Message message) { consumption.consume(decoder.decode(message.getBody())); }
}
