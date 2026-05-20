package com.uniovi.rag.interfaces.rest.auth;

import com.uniovi.rag.application.service.auth.AuthService;
import com.uniovi.rag.application.port.out.UserAccountPort;
import com.uniovi.rag.interfaces.rest.support.ApiEarlyExceptionResolver;
import com.uniovi.rag.interfaces.rest.support.ApiGlobalExceptionHandler;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AuthController.class, ApiGlobalExceptionHandler.class, ApiEarlyExceptionResolver.class})
class ConfirmEmailErrorContractWebMvcTest {

    private static final String AUTH_BASE = "/api/v5/auth";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserAccountPort userAccountPort;

    @Test
    void confirmEmail_invalidToken_returns400WithCode() throws Exception {
        doThrow(new AuthTokenException("CONFIRM_TOKEN_INVALID", "Invalid confirmation token"))
                .when(authService)
                .confirmEmail(any());

        mockMvc.perform(post(AUTH_BASE + "/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONFIRM_TOKEN_INVALID"));
    }

    @Test
    void confirmEmail_expiredToken_returns400WithCode() throws Exception {
        doThrow(new AuthTokenException("CONFIRM_TOKEN_EXPIRED", "Confirmation token expired"))
                .when(authService)
                .confirmEmail(any());

        mockMvc.perform(post(AUTH_BASE + "/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"expired\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONFIRM_TOKEN_EXPIRED"));
    }

    @Test
    void confirmEmail_reusedToken_returns400WithCode() throws Exception {
        doThrow(new AuthTokenException("CONFIRM_TOKEN_ALREADY_USED", "Confirmation token already used"))
                .when(authService)
                .confirmEmail(any());

        mockMvc.perform(post(AUTH_BASE + "/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"used\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONFIRM_TOKEN_ALREADY_USED"));
    }
}
