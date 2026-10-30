package com.geointelli.ai.property.service.controller;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.advice.GlobalExceptionHandler;
import com.geointelli.ai.property.service.service.AlertMonitoringService;
class AlertMonitoringControllerTest {
    final AlertMonitoringService service=mock(AlertMonitoringService.class);
    MockMvc mvc;
    @BeforeEach void setup() { mvc=MockMvcBuilders.standaloneSetup(new AlertMonitoringController(service))
        .setControllerAdvice(new GlobalExceptionHandler()).build(); }
    @Test void evaluationReturns202WithoutBody() throws Exception {
        mvc.perform(post("/api/alerts/1/evaluate")).andExpect(status().isAccepted()).andExpect(content().string(""));
        verify(service).evaluate(1L);
    }
    @Test void missingOwnerReturns404() throws Exception {
        when(service.status(2L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND,"Alert rule not found"));
        mvc.perform(get("/api/alerts/2/monitoring")).andExpect(status().isNotFound());
    }
    @Test void disabledEvaluationReturns409() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT,"Monitoring is disabled")).when(service).evaluate(1L);
        mvc.perform(post("/api/alerts/1/evaluate")).andExpect(status().isConflict());
    }
}
