package com.geointelli.ai.property.service.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

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
}
