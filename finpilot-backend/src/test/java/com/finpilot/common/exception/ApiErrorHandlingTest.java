package com.finpilot.common.exception;

import com.finpilot.auth.service.CustomUserDetails;
import com.finpilot.auth.service.CustomUserDetailsService;
import com.finpilot.auth.service.JwtService;
import com.finpilot.common.enums.Provider;
import com.finpilot.common.enums.Role;
import com.finpilot.common.security.JwtAuthenticationEntryPoint;
import com.finpilot.common.security.SecurityConfig;
import com.finpilot.goal.controller.GoalController;
import com.finpilot.goal.service.GoalService;
import com.finpilot.system.controller.HealthController;
import com.finpilot.user.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {GoalController.class, HealthController.class})
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class})
@TestPropertySource(properties = "app.frontend.allowed-origins=http://localhost:5173")
class ApiErrorHandlingTest {

    private static final String TOKEN = "test-token";
    private static final String EMAIL = "user@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private GoalService goalService;

    private void authenticateAsUser() {
        User user = User.builder()
                .id(1L)
                .fullName("Test User")
                .email(EMAIL)
                .password("encoded")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .enabled(true)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(user);

        when(jwtService.extractUsername(TOKEN)).thenReturn(EMAIL);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(userDetails);
        when(jwtService.isTokenValid(TOKEN, userDetails)).thenReturn(true);
    }

    @Test
    void missingTokenOnProtectedEndpointReturnsJson401() throws Exception {
        mockMvc.perform(get("/api/goals"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.path").value("/api/goals"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void expiredTokenReturns401WithReason() throws Exception {
        when(jwtService.extractUsername(anyString()))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));

        mockMvc.perform(get("/api/goals").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("JWT token has expired"));
    }

    @Test
    void malformedTokenReturns401InsteadOf500() throws Exception {
        when(jwtService.extractUsername(anyString()))
                .thenThrow(new MalformedJwtException("bad token"));

        mockMvc.perform(get("/api/goals").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid JWT token"));
    }

    @Test
    void tokenForDeletedUserReturns401() throws Exception {
        when(jwtService.extractUsername(TOKEN)).thenReturn(EMAIL);
        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenThrow(new UsernameNotFoundException("User not found"));

        mockMvc.perform(get("/api/goals").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("User for this token no longer exists"));
    }

    @Test
    void invalidTokenDoesNotBlockPublicEndpoints() throws Exception {
        when(jwtService.extractUsername(anyString()))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));

        mockMvc.perform(get("/api/health").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void validationErrorsUseUnifiedFormatWithFieldErrors() throws Exception {
        authenticateAsUser();

        mockMvc.perform(post("/api/goals")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/api/goals"))
                .andExpect(jsonPath("$.fieldErrors.goalName").value("Goal name is required"))
                .andExpect(jsonPath("$.fieldErrors.targetAmount").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        authenticateAsUser();

        mockMvc.perform(post("/api/goals")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void invalidUuidPathVariableReturns400InsteadOf500() throws Exception {
        authenticateAsUser();

        mockMvc.perform(get("/api/goals/not-a-uuid").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'not-a-uuid' for parameter 'goalId'"));
    }

    @Test
    void resourceNotFoundReturns404() throws Exception {
        authenticateAsUser();
        UUID goalId = UUID.randomUUID();
        when(goalService.getGoalById(eq(1L), eq(goalId)))
                .thenThrow(new ResourceNotFoundException("Goal not found"));

        mockMvc.perform(get("/api/goals/" + goalId).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Goal not found"));
    }

    @Test
    void unknownEndpointReturns404InsteadOf500() throws Exception {
        authenticateAsUser();

        mockMvc.perform(get("/api/does-not-exist").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Endpoint not found"));
    }

    @Test
    void dataIntegrityViolationReturns409WithoutDatabaseDetails() throws Exception {
        authenticateAsUser();
        when(goalService.createGoal(eq(1L), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uk_secret\""));

        mockMvc.perform(post("/api/goals")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"goalName":"House","targetAmount":100000,"targetDate":"2099-01-01","priority":"HIGH"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Request conflicts with existing data"));
    }

    @Test
    void unexpectedErrorReturnsGenericMessageWithoutInternals() throws Exception {
        authenticateAsUser();
        when(goalService.getUserGoals(1L))
                .thenThrow(new RuntimeException("connection to db-host:5432 refused"));

        mockMvc.perform(get("/api/goals").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("db-host"))));
    }
}
