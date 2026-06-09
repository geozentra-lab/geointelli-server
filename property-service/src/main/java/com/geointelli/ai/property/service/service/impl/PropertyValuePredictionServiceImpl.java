package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.antlr.v4.runtime.dfa.DFAState.PredPrediction;
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
        predictionDto.setGrowthPercentage(calculateGrowthPercentage(predictionDto.getLastSalePrice(), predictionDto.getPredictedPrice()));
        return predictionDto;
    }

    @Override
    public List<PropertyValuePredictionDTO> getPropertyValuePredictionByIds(List<Long> propertyIds) throws PropertyValuePredictionNotFoundException{
        log.info("--------removing duplicates--------------");
        List<Long> uniqueIds = propertyIds.stream().distinct().toList();
        List<PropertyValuePrediction> predictions =
            propertyValuePredictionRepository.findByPropertyIdIn(uniqueIds);

        if (predictions.isEmpty()) {
            throw new PropertyValuePredictionNotFoundException(
                    "No predictions found for given property ids"
            );
        }
        return predictions.stream().map(p -> {
            PropertyValuePredictionDTO dto = propertyValuePredictionMapper.toDTO(p);
            dto.setGrowthPercentage(calculateGrowthPercentage(dto.getLastSalePrice(), dto.getPredictedPrice()));
            return dto;
        }).toList();
    }

    private BigDecimal calculateGrowthPercentage(BigDecimal lastPrice, BigDecimal predictedPrice){
        if(lastPrice == null || lastPrice.compareTo(BigDecimal.ZERO) == 0)
            return BigDecimal.ZERO;

        return predictedPrice
                .subtract(lastPrice)
                .divide(lastPrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)); 
    }
}
