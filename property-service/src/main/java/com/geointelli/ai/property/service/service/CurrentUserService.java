package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.UserDTO;
import com.geointelli.ai.property.service.entity.User;

public interface CurrentUserService {
    UserDTO getCurrentUser();
    User getCurrentUserEntity();
    String getCurrentKeycloakUserId();
}
