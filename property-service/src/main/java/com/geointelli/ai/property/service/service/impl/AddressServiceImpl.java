package com.geointelli.ai.property.service.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.geointelli.ai.property.service.dto.AddressSearchRequest;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.AddressRepository;
import com.geointelli.ai.property.service.service.AddressService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {
    final private AddressRepository addressRepository;
    
    @Override
    public List<Long> getAllPropertiesId(){
        return addressRepository.findAllPropertiesId();
    }

    @Override
    public List<Property> search(AddressSearchRequest req) {
        if (StringUtils.hasText(req.getRawAddress())) {
            List<Property> results = addressRepository
                .findPropertiesByRawAddress(req.getRawAddress());

            if (results.isEmpty()) {
                throw new PropertyNotFoundException(
                    "No property found matching: " + req.getRawAddress()
                );
            }
            return results;
        }

        List<Property> results = addressRepository.findPropertyByAddressFields(
            req.getStreetNumber(),
            req.getStreetName() == null ? null : req.getStreetName().toLowerCase(),
            req.getCity() == null ? null : req.getCity().toLowerCase(),
            req.getZip(),
            req.getUnit() == null ? null : req.getUnit().toLowerCase(),
            req.getState() == null ? null : req.getState().toLowerCase()
        );

        if (results.isEmpty()) {
            throw new PropertyNotFoundException(
                "No property found for the given address fields"
            );
        }

        return results;
    }
}
