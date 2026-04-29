package com.uniovi.rag.interfaces.rest.auth;

import com.uniovi.rag.application.usecase.auth.AuthService;
import com.uniovi.rag.application.port.out.UserAccountPort;
import com.uniovi.rag.interfaces.rest.auth.dto.AuthUserDto;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginResponse;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserAccountPort userAccountPort;

    @Test
    void login_valid_returnsTokens() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.login(any()))
                .thenReturn(new LoginResponse(
                        "access",
                        "refresh",
                        new AuthUserDto(id, "a@b.com", "User", "USER")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.user.email").value("a@b.com"));
    }

    @Test
    void login_invalidEmail_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmEmail_valid_returnsOk() throws Exception {
        doNothing().when(authService).confirmEmail(any());
        mockMvc.perform(post("/api/auth/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"t\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void resendConfirmation_valid_returnsOk() throws Exception {
        doNothing().when(authService).resendConfirmation(any());
        mockMvc.perform(post("/api/auth/resend-confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPassword_valid_returnsOk() throws Exception {
        doNothing().when(authService).forgotPassword(any());
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_valid_returnsOk() throws Exception {
        doNothing().when(authService).resetPassword(any());
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"t\",\"newPassword\":\"password123\"}"))
                .andExpect(status().isOk());
    }
}
