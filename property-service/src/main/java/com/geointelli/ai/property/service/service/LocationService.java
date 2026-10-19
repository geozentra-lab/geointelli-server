package com.geointelli.ai.property.service.service;

import java.util.List;
import com.geointelli.ai.property.service.dto.CountyLocationDTO;
import com.geointelli.ai.property.service.dto.StateDTO;

/** Provides valid geography options for preference selection. */
public interface LocationService {
    /** Returns states currently registered in the database. */
    List<StateDTO> getStates();
    /** Returns registered counties belonging to the selected state. */
    List<CountyLocationDTO> getCounties(Long stateId);
}
