package com.geointelli.ai.property.service.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.geointelli.ai.property.service.advice.GlobalExceptionHandler;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.repository.UserPreferencesRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.PropertySearchService;
import com.geointelli.ai.property.service.service.impl.CurrentUserServiceImpl;
import com.geointelli.ai.property.service.service.impl.RecommendationServiceImpl;

/** Exercises HTTP binding and the real recommendation service's validation. */
class RecommendationControllerTest {
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final UserPreferencesRepository preferences = mock(UserPreferencesRepository.class);
    private final PropertySearchService search = mock(PropertySearchService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        User user = new User();
        user.setId(7L);
        when(currentUser.getCurrentUserEntity()).thenReturn(user);
        when(preferences.findById(7L)).thenReturn(Optional.empty());
        mvc = build(currentUser);
    }

    private MockMvc build(CurrentUserService identity) {
        return MockMvcBuilders.standaloneSetup(new RecommendationController(
            new RecommendationServiceImpl(identity, preferences, search)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(
                new ObjectMapper().registerModule(new JavaTimeModule())))
            .build();
    }

    @Test
    void defaultsAndColdStartHaveAnExplicitContract() throws Exception {
        mvc.perform(get("/api/recommendations"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_PREFERENCES"))
            .andExpect(jsonPath("$.limit").value(20)).andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.limitations").isArray());
    }

    @Test
    void clientCannotChooseAnotherUsersPreferences() throws Exception {
        mvc.perform(get("/api/recommendations").param("userId", "8").param("limit", "50"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.limit").value(50));
        verify(preferences).findById(7L);
        verify(preferences, never()).findById(8L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "51", "abc", "999999999999999999999"})
    void invalidLimitReturns400(String limit) throws Exception {
        mvc.perform(get("/api/recommendations").param("limit", limit)).andExpect(status().isBadRequest());
        verifyNoInteractions(preferences, search);
    }

    @Test
    void anonymousRequestsFailThroughExistingIdentityService() throws Exception {
        build(new CurrentUserServiceImpl(null, null)).perform(get("/api/recommendations"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(preferences, search);
    }
}
