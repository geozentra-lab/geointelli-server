package com.geointelli.ai.property.service.controller.advice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.geointelli.ai.property.service.exception.PropertyValuePredictionNotFoundException;

@ControllerAdvice
public class PropertyValuePredictionControllerExceptionHandler {
    
    @ExceptionHandler(PropertyValuePredictionNotFoundException.class)
    private ResponseEntity<String> handlePropertyValuePredictionNotFoundException(PropertyValuePredictionNotFoundException exception){
        String message = "there is no prediction for the property with id : " + exception.getPropertyId();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(message);
    }

}
