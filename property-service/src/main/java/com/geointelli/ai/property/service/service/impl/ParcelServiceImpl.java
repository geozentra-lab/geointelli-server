package com.geointelli.ai.property.service.service.impl;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.dto.ParcelDTO;
import com.geointelli.ai.property.service.entity.Parcel;
import com.geointelli.ai.property.service.mapper.ParcelMapper;
import com.geointelli.ai.property.service.repository.ParcelRepository;
import com.geointelli.ai.property.service.service.ParcelService;

import lombok.AllArgsConstructor;
import lombok.Data;

@Service
@Data
@AllArgsConstructor
public class ParcelServiceImpl implements ParcelService {
    private final ParcelRepository parcelRepository;
    private final ParcelMapper parcelMapper;

    @Override
    public List<ParcelDTO> getParcelsWithinBoundingBox(double xmin,double ymin,double xmax,double ymax){
        List<Parcel> parcels = parcelRepository.findWithinBoundingBox(xmin,ymin,xmax,ymax);
        return parcels.stream().map(parcel -> parcelMapper.toDTO(parcel)).collect(Collectors.toList());
    }

    @Override
    public Map<String, Parcel> preloadParcels() {
        return parcelRepository.findAll().stream()
                .collect(Collectors.toMap(Parcel::getFolio, Function.identity()));
    }

    @Override
    public List<String> getAllFolios() {
        return parcelRepository.findAllFolios();
    }

    @Override
    public List<ParcelDTO> getByFolio(String folio) {
        return parcelRepository.findByFolio(folio).stream().map(parcel -> parcelMapper.toDTO(parcel)).toList();
    }
}
