package com.renewmate.global.exception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.renewmate.global.incident.IncidentReporter;
import com.renewmate.global.security.JwtProvider;
import com.renewmate.user.entity.User;
import com.renewmate.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClientErrorResponseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private IncidentReporter incidentReporter;

    private String bearerToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.create(
                "Client Error",
                "client-error@example.com",
                "encoded-password"
        ));
        bearerToken = "Bearer " + jwtProvider.createAccessToken(user.getUserId(), user.getEmail());
    }

    @AfterEach
    void shouldNotReportClientErrorsAsIncidents() {
        verify(incidentReporter, never()).report(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("요청 파라미터 제약 위반은 400 VALIDATION_ERROR를 반환한다")
    void shouldReturnBadRequestForConstraintViolation() throws Exception {
        mockMvc.perform(get("/api/subscriptions/upcoming")
                        .param("days", "0")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("읽을 수 없는 JSON 본문은 400 BAD_REQUEST를 반환한다")
    void shouldReturnBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("없는 경로는 404 NOT_FOUND를 반환한다")
    void shouldReturnNotFoundForUnknownPath() throws Exception {
        mockMvc.perform(get("/api/does-not-exist")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405 METHOD_NOT_ALLOWED를 반환한다")
    void shouldReturnMethodNotAllowedForUnsupportedMethod() throws Exception {
        mockMvc.perform(patch("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }
}
