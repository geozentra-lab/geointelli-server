package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.geointelli.ai.property.service.config.ContactRequestProperties;

/** Confirms and mandatory-return failures govern whether the outbox may commit SENT. */
class RabbitContactRequestEventPublisherImplTest {
    @Test void sendsFrozenBytesWithStableIdentityAndWaitsForAck() throws Exception {
        RabbitTemplate rabbit=mock(RabbitTemplate.class);
        var settings=new ContactRequestProperties();
        doAnswer(call->{
            Message message=call.getArgument(2);
            CorrelationData correlation=call.getArgument(3);
            assertEquals("property-contact-1",message.getMessageProperties().getMessageId());
            assertEquals("CONTACT_REQUEST",message.getMessageProperties().getType());
            assertEquals(MessageDeliveryMode.PERSISTENT,message.getMessageProperties().getDeliveryMode());
            assertEquals("{\"eventId\":\"property-contact-1\"}",new String(message.getBody(),StandardCharsets.UTF_8));
            correlation.getFuture().complete(new CorrelationData.Confirm(true,null));
            return null;
        }).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        new RabbitContactRequestEventPublisherImpl(rabbit,settings).publish("property-contact-1","{\"eventId\":\"property-contact-1\"}");
    }

    @Test void negativeConfirmAndUnroutableAckBothFail() {
        for(boolean returned:new boolean[]{false,true}) {
            RabbitTemplate rabbit=mock(RabbitTemplate.class);
            doAnswer(call->{
                Message message=call.getArgument(2);
                CorrelationData correlation=call.getArgument(3);
                if(returned) correlation.setReturned(new ReturnedMessage(message,312,"NO_ROUTE","exchange","routing"));
                correlation.getFuture().complete(new CorrelationData.Confirm(returned,null));
                return null;
            }).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
            assertThrows(IllegalStateException.class,()->new RabbitContactRequestEventPublisherImpl(rabbit,new ContactRequestProperties())
                .publish("property-contact-1","{}"));
        }
    }
}

