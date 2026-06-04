package com.uniovi.rag.interfaces.rest.auth;

import com.uniovi.rag.application.service.auth.AuthService;
import com.uniovi.rag.application.port.out.UserAccountPort;
import com.uniovi.rag.interfaces.rest.auth.dto.AuthUserDto;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginResponse;
import com.uniovi.rag.interfaces.rest.auth.dto.RegisterResponse;
import com.uniovi.rag.interfaces.rest.auth.AuthTokenException;
import com.uniovi.rag.interfaces.rest.support.ApiEarlyExceptionResolver;
import com.uniovi.rag.interfaces.rest.support.ApiGlobalExceptionHandler;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AuthController.class, ApiGlobalExceptionHandler.class, ApiEarlyExceptionResolver.class})
class AuthControllerTest {

    private static final String AUTH_BASE = "/api/v5/auth";

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

        mockMvc.perform(post(AUTH_BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.user.email").value("a@b.com"));
    }

    @Test
    void login_invalidCredentials_returnsCanonical401Json() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());
        mockMvc.perform(post(AUTH_BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_invalidEmail_returnsBadRequest() throws Exception {
        mockMvc.perform(post(AUTH_BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicateEmail_returnsCanonical409Json() throws Exception {
        when(authService.register(any())).thenThrow(new DuplicateEmailException());
        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"N\",\"email\":\"a@b.com\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void register_pendingEmailVerification_returnsAccepted() throws Exception {
        when(authService.register(any()))
                .thenReturn(new RegisterResponse("PENDING_EMAIL_VERIFICATION", null));

        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"N\",\"email\":\"a@b.com\",\"password\":\"password123\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_EMAIL_VERIFICATION"))
                .andExpect(jsonPath("$.login").doesNotExist());
    }

    @Test
    void register_registered_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        LoginResponse login = new LoginResponse(
                "access",
                "refresh",
                new AuthUserDto(id, "a@b.com", "User", "USER"));
        when(authService.register(any()))
                .thenReturn(new RegisterResponse("REGISTERED", login));

        mockMvc.perform(post(AUTH_BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"N\",\"email\":\"a@b.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.login.accessToken").value("access"));
    }

    @Test
    void login_invalidEmail_returnsCanonicalValidationJson() throws Exception {
        mockMvc.perform(post(AUTH_BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors").isArray())
                .andExpect(jsonPath("$.path").value(AUTH_BASE + "/login"));
    }

    @Test
    void login_wrongMethod_returnsCanonical405Json() throws Exception {
        mockMvc.perform(get(AUTH_BASE + "/login").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void login_unsupportedMediaType_returnsCanonical415Json() throws Exception {
        mockMvc.perform(post(AUTH_BASE + "/login")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void confirmEmail_valid_returnsOk() throws Exception {
        doNothing().when(authService).confirmEmail(any());
        mockMvc.perform(post(AUTH_BASE + "/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"t\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void resendConfirmation_valid_returnsOk() throws Exception {
        doNothing().when(authService).resendConfirmation(any());
        mockMvc.perform(post(AUTH_BASE + "/resend-confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPassword_valid_returnsNeutralJson() throws Exception {
        doNothing().when(authService).forgotPassword(any(), anyString(), anyString());
        mockMvc.perform(post(AUTH_BASE + "/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUEST_ACCEPTED"))
                .andExpect(jsonPath("$.message").value(
                        "If an account exists for that email, a reset link will be sent"));
    }

    @Test
    void forgotPassword_distinctEmails_returnIdenticalNeutralBodies_forAntiEnumeration() throws Exception {
        doNothing().when(authService).forgotPassword(any(), anyString(), anyString());
        String existing =
                mockMvc.perform(post(AUTH_BASE + "/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"known@example.com\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String unknown =
                mockMvc.perform(post(AUTH_BASE + "/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"ghost-not-found@example.com\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertEquals(existing, unknown);
    }

    @Test
    void refresh_valid_returnsTokens() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.refresh(any()))
                .thenReturn(new LoginResponse(
                        "acc",
                        "ref",
                        new AuthUserDto(id, "u@test.com", "User", "USER")));

        mockMvc.perform(post(AUTH_BASE + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"rt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("acc"))
                .andExpect(jsonPath("$.refreshToken").value("ref"))
                .andExpect(jsonPath("$.user.email").value("u@test.com"));
    }

    @Test
    void refresh_invalidToken_returns401InvalidCredentials() throws Exception {
        when(authService.refresh(any())).thenThrow(new InvalidCredentialsException());
        mockMvc.perform(post(AUTH_BASE + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_emailNotVerified_returns403WithCode() throws Exception {
        when(authService.login(any())).thenThrow(new EmailNotVerifiedException());
        mockMvc.perform(post(AUTH_BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pending@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void refresh_emailNotVerified_returns403WithCode() throws Exception {
        when(authService.refresh(any())).thenThrow(new EmailNotVerifiedException());
        mockMvc.perform(post(AUTH_BASE + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void resetPassword_valid_returnsOk() throws Exception {
        doNothing().when(authService).resetPassword(any());
        mockMvc.perform(post(AUTH_BASE + "/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"t\",\"newPassword\":\"password123\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_invalidToken_returns400WithCode() throws Exception {
        doThrow(new AuthTokenException("RESET_TOKEN_INVALID", "Invalid reset token"))
                .when(authService)
                .resetPassword(any());
        mockMvc.perform(post(AUTH_BASE + "/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"bad\",\"newPassword\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    void resetPassword_expiredToken_returns400WithCode() throws Exception {
        doThrow(new AuthTokenException("RESET_TOKEN_EXPIRED", "Reset token expired"))
                .when(authService)
                .resetPassword(any());
        mockMvc.perform(post(AUTH_BASE + "/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"expired\",\"newPassword\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_EXPIRED"));
    }

    @Test
    void resetPassword_reusedToken_returns400WithCode() throws Exception {
        doThrow(new AuthTokenException("RESET_TOKEN_ALREADY_USED", "Reset token already used"))
                .when(authService)
                .resetPassword(any());
        mockMvc.perform(post(AUTH_BASE + "/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"used\",\"newPassword\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_ALREADY_USED"));
    }

    @Test
    void resetPassword_disabled_returns400WithCode() throws Exception {
        doThrow(new AuthTokenException("PASSWORD_RESET_DISABLED", "Password reset disabled"))
                .when(authService)
                .resetPassword(any());
        mockMvc.perform(post(AUTH_BASE + "/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"t\",\"newPassword\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_DISABLED"));
    }

}
