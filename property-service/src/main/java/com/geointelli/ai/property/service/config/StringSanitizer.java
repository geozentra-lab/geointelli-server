package com.geointelli.ai.property.service.config;

import java.lang.reflect.Field;

import org.mapstruct.AfterMapping;
import org.mapstruct.MappingTarget;
import org.springframework.stereotype.Component;

@Component
public class StringSanitizer {
    
    @AfterMapping
    public void sanitize(@MappingTarget Object target) {
        if (target == null) return;
        Field[] fields = target.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (field.getType().equals(String.class)) {
                field.setAccessible(true);
                try {
                    String value = (String) field.get(target);
                    if (value != null) {
                        field.set(target, value.replace("\u0000", "").trim());
                    }
                } catch (IllegalAccessException ignored) {}
            }
        }
    }
}