package com.uniovi.rag.interfaces.rest.auth;

import com.uniovi.rag.application.service.auth.OauthLoginService;
import com.uniovi.rag.interfaces.rest.auth.InvalidCredentialsException;
import com.uniovi.rag.interfaces.rest.auth.dto.AuthUserDto;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginResponse;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OauthController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({OauthController.class, ApiGlobalExceptionHandler.class, ApiEarlyExceptionResolver.class})
@TestPropertySource(properties = "rag.api.product-base-path=/api/v5")
class OauthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OauthLoginService oauthLoginService;

    @Test
    void startGoogle_redirectsToAuthorizationUrl() throws Exception {
        when(oauthLoginService.googleStartUrl(any())).thenReturn("https://accounts.google.com/o/oauth2/v2/auth");

        mockMvc.perform(get("/api/v5/auth/oauth/google/start")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://accounts.google.com/o/oauth2/v2/auth"));
    }

    @Test
    void callbackGoogle_v5Route_redirectsToWebappUrl() throws Exception {
        when(oauthLoginService.handleGoogleCallback(eq("c"), eq("s"), isNull()))
                .thenReturn("http://localhost:3000/en/oauth/callback/google?code=x");

        mockMvc.perform(get("/api/v5/auth/oauth/google/callback")
                        .param("code", "c")
                        .param("state", "s"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/en/oauth/callback/google?code=x"));
    }

    @Test
    void exchange_v5Route_returnsLoginResponse() throws Exception {
        UUID id = UUID.randomUUID();
        when(oauthLoginService.exchange("raw-code"))
                .thenReturn(new LoginResponse(
                        "access",
                        "refresh",
                        new AuthUserDto(id, "u@test.com", "U", "USER")));

        mockMvc.perform(post("/api/v5/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"raw-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.user.email").value("u@test.com"));
    }

    @Test
    void exchange_v5Route_blankCode_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v5/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exchange_v5Route_unknownCode_returns401InvalidCredentials() throws Exception {
        when(oauthLoginService.exchange("missing-exchange-code")).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v5/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"missing-exchange-code\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void callbackGoogle_providerError_redirectsToWebappWithOauthError() throws Exception {
        when(oauthLoginService.handleGoogleCallback(isNull(), eq("st"), eq("access_denied")))
                .thenReturn("http://localhost:3000/en/login?oauth=error");

        mockMvc.perform(get("/api/v5/auth/oauth/google/callback")
                        .param("state", "st")
                        .param("error", "access_denied"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/en/login?oauth=error"));
    }

    @Test
    void callbackGoogle_missingCode_redirectsToWebappWithOauthError() throws Exception {
        when(oauthLoginService.handleGoogleCallback(isNull(), eq("st"), isNull()))
                .thenReturn("http://localhost:3000/en/login?oauth=error");

        mockMvc.perform(get("/api/v5/auth/oauth/google/callback").param("state", "st"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/en/login?oauth=error"));
    }

    @Test
    void callbackGoogle_invalidState_redirectsToWebappWithInvalidStateQuery() throws Exception {
        when(oauthLoginService.handleGoogleCallback(eq("c"), eq("bad-state"), isNull()))
                .thenReturn("http://localhost:3000/en/login?oauth=invalid_state");

        mockMvc.perform(get("/api/v5/auth/oauth/google/callback")
                        .param("code", "c")
                        .param("state", "bad-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/en/login?oauth=invalid_state"));
    }

}
