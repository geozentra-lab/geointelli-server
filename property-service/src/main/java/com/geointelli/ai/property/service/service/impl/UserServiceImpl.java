package com.geointelli.ai.property.service.service.impl;

import org.mapstruct.control.MappingControl.Use;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.dto.UserDTO;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.mapper.UserMapper;
import com.geointelli.ai.property.service.repository.UserRepository;
import com.geointelli.ai.property.service.service.UserService;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public UserDTO getOrCreateUser(Jwt jwt) {
        String keycloakUserId = jwt.getSubject();
        return userMapper.toDTO(userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseGet(() -> createUserFromJwt(jwt)));
    }

    public User createUserFromJwt(Jwt jwt){
        // A new account's first page load sends several requests at once; insert-if-absent lets
        // every one of them succeed instead of all but one failing on the unique Keycloak ID.
        userRepository.insertIfAbsent(
            jwt.getSubject(),
            jwt.getClaimAsString("preferred_username"),
            jwt.getClaimAsString("email"),
            jwt.getClaimAsString("given_name"),
            jwt.getClaimAsString("family_name"));
        return userRepository.findByKeycloakUserId(jwt.getSubject())
            .orElseThrow(() -> new IllegalStateException("User provisioning failed"));
    }
}
