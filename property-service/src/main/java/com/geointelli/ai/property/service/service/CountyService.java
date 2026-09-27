package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.entity.County;

public interface CountyService {
    void ensureExists(String county, String state);

    County resolve(String county, String state);

    County miamiDade();
}
