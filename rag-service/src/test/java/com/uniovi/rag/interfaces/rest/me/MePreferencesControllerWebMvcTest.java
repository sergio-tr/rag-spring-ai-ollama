package com.uniovi.rag.interfaces.rest.me;

import com.uniovi.rag.application.service.me.UserMePreferenceService;
import com.uniovi.rag.interfaces.rest.dto.me.MePreferencesResponse;
import com.uniovi.rag.interfaces.rest.support.RagWebMvcTestApplication;
import com.uniovi.rag.security.RagPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MePreferencesController.class)
@ContextConfiguration(classes = RagWebMvcTestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MePreferencesController.class)
class MePreferencesControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserMePreferenceService userMePreferenceService;

    private UUID userId;

    @BeforeEach
    void setUser() {
        userId = UUID.randomUUID();
        RagPrincipal principal = new RagPrincipal(userId, "u@test", "USER");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_returnsPreferences() throws Exception {
        when(userMePreferenceService.get(eq(userId)))
                .thenReturn(new MePreferencesResponse(1, Map.of("locale", "en")));

        mockMvc.perform(get("/api/v5/me/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.preferences.locale").value("en"));
    }

    @Test
    void put_returnsUpdated() throws Exception {
        when(userMePreferenceService.put(eq(userId), any()))
                .thenReturn(new MePreferencesResponse(1, Map.of("locale", "es")));

        mockMvc.perform(
                        put("/api/v5/me/preferences")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"preferences\":{\"locale\":\"es\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(1));
    }
}
