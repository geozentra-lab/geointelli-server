package com.geointelli.ai.property.service.service.impl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.config.AlertMonitoringProperties;
import com.geointelli.ai.property.service.dto.AlertEventEnvelopeDTO;
import com.geointelli.ai.property.service.entity.AlertEventType;
class RabbitAlertEventPublisherImplTest {
    final RabbitTemplate rabbit=mock(RabbitTemplate.class);
    final AlertMonitoringProperties settings=new AlertMonitoringProperties();
    final RabbitAlertEventPublisherImpl service=new RabbitAlertEventPublisherImpl(rabbit,new ObjectMapper(),settings);
    final AlertEventEnvelopeDTO event=new AlertEventEnvelopeDTO("id-5","property-service",1,AlertEventType.NEW_MATCH,
        "user",1L,2L,null,"generation-1",null,null);
    void confirm(boolean ack,boolean returned) {
        doAnswer(call->{
            CorrelationData correlation=call.getArgument(3);
            Message message=call.getArgument(2);
            assertEquals("id-5",message.getMessageProperties().getMessageId());
            if (returned) correlation.setReturned(new ReturnedMessage(new Message(new byte[0],new MessageProperties()),312,"NO_ROUTE","exchange","route"));
            correlation.getFuture().complete(new CorrelationData.Confirm(ack,null)); return null;
        }).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
    }
    @Test void acknowledgementSucceeds() { confirm(true,false); assertDoesNotThrow(()->service.publish(event)); }
    @Test void nackFails() { confirm(false,false); assertThrows(IllegalStateException.class,()->service.publish(event)); }
    @Test void returnedMessageFailsEvenIfAcknowledged() { confirm(true,true); assertThrows(IllegalStateException.class,()->service.publish(event)); }
}
