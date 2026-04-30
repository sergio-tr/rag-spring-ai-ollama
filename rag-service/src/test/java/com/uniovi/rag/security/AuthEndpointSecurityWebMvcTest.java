package com.uniovi.rag.security;

import com.uniovi.rag.application.port.out.UserAccountPort;
import com.uniovi.rag.application.usecase.auth.AuthService;
import com.uniovi.rag.application.usecase.auth.OauthLoginService;
import com.uniovi.rag.configuration.SecurityConfiguration;
import com.uniovi.rag.domain.UserRole;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.interfaces.rest.auth.AuthController;
import com.uniovi.rag.interfaces.rest.auth.OauthController;
import com.uniovi.rag.interfaces.rest.auth.dto.AuthUserDto;
import com.uniovi.rag.interfaces.rest.auth.dto.LoginResponse;
import com.uniovi.rag.interfaces.rest.support.ApiEarlyExceptionResolver;
import com.uniovi.rag.interfaces.rest.support.ApiGlobalExceptionHandler;
import com.uniovi.rag.interfaces.rest.support.RagApiExceptionHandler;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, OauthController.class})
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({
        AuthController.class,
        OauthController.class,
        SecurityConfiguration.class,
        JwtService.class,
        JwtAuthenticationFilter.class,
        ApiGlobalExceptionHandler.class,
        RagApiExceptionHandler.class,
        ApiEarlyExceptionResolver.class
})
@TestPropertySource(properties = {
        "rag.jwt.secret=test-secret-key-for-jwt-signing-must-be-long-enough-32",
        "rag.api.product-base-path=/api/v5"
})
class AuthEndpointSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserAccountPort userAccountPort;

    @MockitoBean
    private OauthLoginService oauthLoginService;

    @Test
    void authLogin_publicWithoutToken_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        when(authService.login(any())).thenReturn(new LoginResponse(
                "access",
                "refresh",
                new AuthUserDto(id, "u@test", "User", "USER")));

        mockMvc.perform(post("/api/v5/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"u@test\",\"password\":\"secret\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"));
    }

    @Test
    void authMe_withoutToken_returnsJson401() throws Exception {
        mockMvc.perform(get("/api/v5/auth/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.path").value("/api/v5/auth/me"));
    }

    @Test
    void authMe_withToken_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        String token = jwtService.createAccessToken(id, "u@test", "USER");
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn("u@test");
        when(user.getName()).thenReturn("User");
        when(user.getRole()).thenReturn(UserRole.USER);
        when(userAccountPort.findById(id)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v5/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("u@test"));
    }

    @Test
    void oauthV5Start_withoutToken_isPublicAndRedirects() throws Exception {
        when(oauthLoginService.googleStartUrl(any())).thenReturn("https://accounts.google.com/o/oauth2/v2/auth");

        mockMvc.perform(get("/api/v5/auth/oauth/google/start"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void oauthV5Callback_withoutToken_isPublic() throws Exception {
        when(oauthLoginService.handleGoogleCallback(eq("code"), eq("state"), isNull()))
                .thenReturn("http://localhost:3000/en/oauth/callback/google?code=x");

        mockMvc.perform(get("/api/v5/auth/oauth/google/callback")
                        .param("code", "code")
                        .param("state", "state"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void oauthV5Exchange_withoutToken_isPublic() throws Exception {
        UUID id = UUID.randomUUID();
        when(oauthLoginService.exchange("raw-code"))
                .thenReturn(new LoginResponse(
                        "access",
                        "refresh",
                        new AuthUserDto(id, "oauth@test", "OAuth User", "USER")));

        mockMvc.perform(post("/api/v5/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"raw-code\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"));
    }

    @Test
    void productEndpoint_withoutToken_stillRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/v5/projects").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
