package com.geointelli.ai.notification.service.config;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import com.geointelli.ai.notification.service.exception.InvalidAlertEventException;
import com.geointelli.ai.notification.service.service.DeadLetterService;
class NotificationRetryTest {
    final NotificationRabbitConfig config=new NotificationRabbitConfig();
    final NotificationProperties settings=new NotificationProperties();
    final DeadLetterService recovery=mock(DeadLetterService.class);
    final MethodInvocation invocation=mock(MethodInvocation.class);
    final Message message=new Message(new byte[]{1},new MessageProperties());
    NotificationRetryTest() {
        settings.setRetryInitialMillis(1);settings.setRetryMaxMillis(2);
        // Container advice receives (Channel, Message), not the user's listener signature.
        when(invocation.getArguments()).thenReturn(new Object[]{null,message});
    }
    @Test void transientFailureRetriesAndThenSucceeds() throws Throwable {
        when(invocation.proceed()).thenThrow(new IllegalStateException()).thenReturn(null);
        config.notificationRetryAdvice(settings,recovery).invoke(invocation);
        verify(invocation,times(2)).proceed();verifyNoInteractions(recovery);
    }
    @Test void exhaustedAttemptsPublishOneRecoveryCopy() throws Throwable {
        when(invocation.proceed()).thenThrow(new IllegalStateException("private database details"));
        config.notificationRetryAdvice(settings,recovery).invoke(invocation);
        verify(invocation,times(3)).proceed();verify(recovery).publish(message,"PROCESSING_FAILED",3);
    }
    @Test void permanentContractFailureUsesStableCode() throws Throwable {
        when(invocation.proceed()).thenThrow(new InvalidAlertEventException("UNSUPPORTED_EVENT_TYPE"));
        config.notificationRetryAdvice(settings,recovery).invoke(invocation);
        verify(recovery).publish(message,"UNSUPPORTED_EVENT_TYPE",3);
    }
    @Test void recoveryFailureRequeuesOriginal() throws Throwable {
        when(invocation.proceed()).thenThrow(new IllegalStateException());
        doThrow(new IllegalStateException()).when(recovery).publish(any(),anyString(),anyInt());
        assertThrows(ImmediateRequeueAmqpException.class,()->config.notificationRetryAdvice(settings,recovery).invoke(invocation));
    }
    @Test void mainQueueRemainsCompatibleWithExistingProducerDeclarations() {
        Queue main=config.notificationQueues(settings).getDeclarablesByType(Queue.class).stream()
            .filter(q->q.getName().equals(settings.getQueue())).findFirst().orElseThrow();
        assertTrue(main.isDurable());assertTrue(main.getArguments().isEmpty());
    }
}
