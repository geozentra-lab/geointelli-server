package com.geointelli.ai.property.service.service.impl;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.CountyLocationDTO;
import com.geointelli.ai.property.service.dto.StateDTO;
import com.geointelli.ai.property.service.mapper.LocationMapper;
import com.geointelli.ai.property.service.repository.CountyRepository;
import com.geointelli.ai.property.service.repository.StateRepository;
import com.geointelli.ai.property.service.service.LocationService;
import lombok.RequiredArgsConstructor;

/** Reads registered states and counties for the location picker. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationServiceImpl implements LocationService {
    private final StateRepository states;
    private final CountyRepository counties;
    private final LocationMapper mapper;

    @Override
    public List<StateDTO> getStates() {
        return states.findAll(Sort.by("name")).stream().map(mapper::toDTO).toList();
    }

    @Override
    public List<CountyLocationDTO> getCounties(Long stateId) {
        if (!states.existsById(stateId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "State not found");
        return counties.findByState_IdOrderByNameAsc(stateId).stream().map(mapper::toDTO).toList();
    }
}
