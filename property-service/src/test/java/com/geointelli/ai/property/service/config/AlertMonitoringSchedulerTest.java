package com.geointelli.ai.property.service.config;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import com.geointelli.ai.property.service.exception.AlertEvaluationException;
import com.geointelli.ai.property.service.service.AlertEvaluationService;
import com.geointelli.ai.property.service.service.AlertOutboxPublishingService;
class AlertMonitoringSchedulerTest {
    final AlertEvaluationService evaluation=mock(AlertEvaluationService.class);
    final AlertOutboxPublishingService publishing=mock(AlertOutboxPublishingService.class);
    final AlertMonitoringProperties settings=new AlertMonitoringProperties();
    final AlertMonitoringScheduler scheduler=new AlertMonitoringScheduler(evaluation,publishing,settings);
    @Test void boundsEvaluationWorkPerTick() {
        settings.setStepsPerTick(3); when(evaluation.processNextBatch()).thenReturn(true);
        scheduler.evaluate(); verify(evaluation).prepare(); verify(evaluation,times(3)).processNextBatch();
    }
    @Test void failedChunkIsRecordedAndOtherRulesCanContinue() {
        when(evaluation.processNextBatch()).thenThrow(new AlertEvaluationException(1L,new IllegalStateException())).thenReturn(false);
        scheduler.evaluate(); verify(evaluation).recordFailure(1L); verify(evaluation,times(2)).processNextBatch();
    }
    @Test void disabledPublisherDoesNotPoll() { scheduler.publish(); verifyNoInteractions(publishing); }
    @Test void boundsPublishWorkPerTick() {
        settings.getPublisher().setEnabled(true); settings.getPublisher().setBatchSize(2);
        when(publishing.processNext()).thenReturn(true); scheduler.publish(); verify(publishing,times(2)).processNext();
    }
}
