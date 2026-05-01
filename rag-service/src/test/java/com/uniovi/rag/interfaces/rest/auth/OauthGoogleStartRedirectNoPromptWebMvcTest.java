package com.uniovi.rag.interfaces.rest.auth;

import com.uniovi.rag.application.usecase.auth.OauthLoginService;
import com.uniovi.rag.application.port.out.UserAccountPort;
import com.uniovi.rag.infrastructure.persistence.OauthIdentityRepository;
import com.uniovi.rag.infrastructure.persistence.OauthLoginExchangeCodeRepository;
import com.uniovi.rag.infrastructure.persistence.OauthLoginStateTokenRepository;
import com.uniovi.rag.interfaces.rest.support.ApiEarlyExceptionResolver;
import com.uniovi.rag.interfaces.rest.support.ApiGlobalExceptionHandler;
import com.uniovi.rag.security.JwtService;
import com.uniovi.rag.testsupport.webmvc.RagWebMvcTestApplication;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OauthController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({OauthController.class, OauthLoginService.class, ApiGlobalExceptionHandler.class, ApiEarlyExceptionResolver.class})
@TestPropertySource(
        properties = {
            "rag.api.product-base-path=/api/v5",
            "rag.auth.oauth.enabled=true",
            "rag.auth.oauth.google.client-id=test-google-client-id",
            "rag.auth.oauth.google.client-secret=test-google-secret",
            "rag.auth.webapp-base-url=http://localhost:3000",
            "rag.auth.backend-base-url=http://localhost:9000",
            "rag.auth.oauth.google.prompt-select-account=false",
        })
class OauthGoogleStartRedirectNoPromptWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAccountPort userAccountPort;

    @MockitoBean
    private OauthIdentityRepository oauthIdentityRepository;

    @MockitoBean
    private OauthLoginExchangeCodeRepository oauthLoginExchangeCodeRepository;

    @MockitoBean
    private OauthLoginStateTokenRepository oauthLoginStateTokenRepository;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @Test
    void startGoogle_promptDisabled_redirectsWithoutPromptParameter() throws Exception {
        mockMvc.perform(get("/api/v5/auth/oauth/google/start").param("locale", "en"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.startsWith("https://accounts.google.com/o/oauth2/v2/auth")))
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.not(Matchers.containsString("prompt="))))
                .andExpect(header().string(HttpHeaders.LOCATION, Matchers.containsString("state=")));
    }
}
