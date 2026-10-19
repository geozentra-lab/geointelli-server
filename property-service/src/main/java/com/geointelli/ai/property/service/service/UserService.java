package com.geointelli.ai.property.service.service;


import org.springframework.security.oauth2.jwt.Jwt;

import com.geointelli.ai.property.service.dto.UserDTO;

public interface UserService {
    UserDTO getOrCreateUser(Jwt jwt);
}
