package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.dto.PropertyValuePredictionDTO;
import com.geointelli.ai.property.service.entity.PropertyValuePrediction;
import com.geointelli.ai.property.service.exception.PropertyValuePredictionNotFoundException;
import com.geointelli.ai.property.service.mapper.PropertyValuePredictionMapper;
import com.geointelli.ai.property.service.repository.PropertyValuePredictionRepository;
import com.geointelli.ai.property.service.service.PropertyValuePredictionService;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Service
@Data
@Slf4j
public class PropertyValuePredictionServiceImpl implements PropertyValuePredictionService {
    private final PropertyValuePredictionRepository propertyValuePredictionRepository;
    private final PropertyValuePredictionMapper propertyValuePredictionMapper;

    @Override
    public PropertyValuePredictionDTO getPropertyValuePredictionById(Long propertyId) throws PropertyValuePredictionNotFoundException{
        PropertyValuePrediction prediction = propertyValuePredictionRepository.findByPropertyId(propertyId)
                .orElseThrow(() -> new PropertyValuePredictionNotFoundException("prediction value not found for property with id : " + propertyId));
        PropertyValuePredictionDTO predictionDto = propertyValuePredictionMapper.toDTO(prediction);
        predictionDto.setGrowthPercentage(calculateGrowthPercentage(prediction));
        return predictionDto;
    }

    @Override
    public List<PropertyValuePredictionDTO> getPropertyValuePredictionByIds(List<Long> propertyIds) throws PropertyValuePredictionNotFoundException{
        log.info("--------removing duplicates--------------");
        List<Long> uniqueIds = propertyIds.stream().distinct().toList();
        List<PropertyValuePrediction> predictions =
            propertyValuePredictionRepository.findByPropertyIdIn(uniqueIds);

        if (predictions.isEmpty()) {
            throw new PropertyValuePredictionNotFoundException("No predictions found for given property ids");
        }
        return predictions.stream().map(p -> {
            PropertyValuePredictionDTO dto = propertyValuePredictionMapper.toDTO(p);
            dto.setGrowthPercentage(calculateGrowthPercentage(p));
            return dto;
        }).toList();
    }

    /** One-year projected value change, not historical appreciation; unavailable inputs remain null. */
    private BigDecimal calculateGrowthPercentage(PropertyValuePrediction prediction) {
        if (!"supported".equals(prediction.getValuationStatus())
                && !"limited".equals(prediction.getValuationStatus())) return null;
        BigDecimal currentValue = prediction.getPredictedPrice();
        BigDecimal futureValue = prediction.getForecast1y();
        if (currentValue == null || currentValue.signum() <= 0
                || futureValue == null || futureValue.signum() <= 0) return null;
        return futureValue.subtract(currentValue).multiply(BigDecimal.valueOf(100))
            .divide(currentValue, 4, RoundingMode.HALF_UP);
    }
}
