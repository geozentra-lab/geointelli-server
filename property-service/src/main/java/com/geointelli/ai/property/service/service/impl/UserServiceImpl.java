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
        User user = new User();
        user.setKeycloakUserId(jwt.getSubject());
        user.setUsername(jwt.getClaimAsString("preferred_username"));
        user.setEmail(jwt.getClaimAsString("email"));
        user.setFirstName(jwt.getClaimAsString("given_name"));
        user.setLastName(jwt.getClaimAsString("family_name"));
        return userRepository.save(user);
    }
}
