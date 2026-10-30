package com.geointelli.ai.notification.service.service.impl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.geointelli.ai.notification.service.config.NotificationProperties;
class DeadLetterServiceImplTest {
    final RabbitTemplate rabbit=mock(RabbitTemplate.class);
    final NotificationProperties settings=new NotificationProperties();
    final DeadLetterServiceImpl service=new DeadLetterServiceImpl(rabbit,settings);
    final Message original=new Message(new byte[]{1,2,3},new MessageProperties());
    void confirm(boolean ack,boolean returned) {
        doAnswer(call->{
            Message failed=call.getArgument(2);CorrelationData correlation=call.getArgument(3);
            assertArrayEquals(original.getBody(),failed.getBody());
            assertEquals(MessageDeliveryMode.PERSISTENT,failed.getMessageProperties().getDeliveryMode());
            assertEquals("PROCESSING_FAILED",failed.getMessageProperties().getHeader("failure-code"));
            if(returned)correlation.setReturned(new ReturnedMessage(failed,312,"NO_ROUTE","dead","failed"));
            correlation.getFuture().complete(new CorrelationData.Confirm(ack,null));return null;
        }).when(rabbit).send(eq(settings.getDeadLetterExchange()),eq(settings.getDeadLetterRoutingKey()),any(Message.class),any(CorrelationData.class));
    }
    @Test void confirmedCopyPreservesBodyAndStableMetadata() { confirm(true,false);assertDoesNotThrow(()->service.publish(original,"PROCESSING_FAILED",3)); }
    @Test void negativeConfirmationFailsRecovery() { confirm(false,false);assertThrows(IllegalStateException.class,()->service.publish(original,"PROCESSING_FAILED",3)); }
    @Test void returnedMessageFailsRecoveryEvenWhenAcked() { confirm(true,true);assertThrows(IllegalStateException.class,()->service.publish(original,"PROCESSING_FAILED",3)); }
    @Test void offlineBrokerFailsRecovery() {
        doThrow(new IllegalStateException()).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        assertThrows(IllegalStateException.class,()->service.publish(original,"PROCESSING_FAILED",3));
    }
    @Test void confirmationTimeoutDoesNotAcknowledgeOriginal() {
        settings.setConfirmTimeoutSeconds(1);
        assertThrows(IllegalStateException.class,()->service.publish(original,"PROCESSING_FAILED",3));
    }
}
