package com.uniovi.rag.service.admin;

import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.infrastructure.persistence.AllowedModelRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AllowedModelEntity;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelCheckRequest;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelUpsertRequest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminModelsServiceTest {

    @Mock
    private AllowedModelRepository allowedModelRepository;

    @Mock
    private OllamaApiClient ollamaApiClient;

    private AdminModelsService svc() {
        return new AdminModelsService(allowedModelRepository, ollamaApiClient);
    }

    @Test
    void check_installed_ok() throws Exception {
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of("llama3:latest"));
        var res = svc().check(new AdminModelCheckRequest("llama3:latest", AllowedModelType.LLM, false));
        assertThat(res.existsLocal()).isTrue();
        assertThat(res.errorCode()).isNull();
    }

    @Test
    void check_missing_pullDisabled_returnsModelNotFound() throws Exception {
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of());
        var res = svc().check(new AdminModelCheckRequest("m", AllowedModelType.LLM, false));
        assertThat(res.existsLocal()).isFalse();
        assertThat(res.errorCode()).isEqualTo("MODEL_NOT_FOUND");
    }

    @Test
    void upsert_missing_butDisabled_savesAsUnavailable() throws Exception {
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of());
        when(allowedModelRepository.findByNameAndType(eq("m"), eq(AllowedModelType.LLM))).thenReturn(Optional.empty());
        when(allowedModelRepository.save(any(AllowedModelEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        var out = svc().upsert(new AdminModelUpsertRequest("m", null, AllowedModelType.LLM, false, false, List.of()));
        assertThat(out.enabled()).isFalse();
        assertThat(out.available()).isFalse();
        assertThat(out.modelId()).isEqualTo("m");
    }

    @Test
    void upsert_missing_pullDisabled_throws() throws Exception {
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of());
        assertThatThrownBy(() -> svc().upsert(new AdminModelUpsertRequest("m", null, AllowedModelType.LLM, true, false, List.of())))
                .isInstanceOf(AdminModelCheckException.class)
                .hasMessageContaining("Cannot enable");
    }

    @Test
    void upsert_missing_pullEnabled_pulls_then_savesEnabled() throws Exception {
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of()).thenReturn(Set.of("m:latest"));
        Mockito.doNothing().when(ollamaApiClient).pullModel(eq("m"), anyLong());
        when(allowedModelRepository.findByNameAndType(eq("m:latest"), eq(AllowedModelType.LLM))).thenReturn(Optional.empty());
        when(allowedModelRepository.save(any(AllowedModelEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var out = svc().upsert(new AdminModelUpsertRequest("m", "M", AllowedModelType.LLM, true, true, List.of()));
        assertThat(out.enabled()).isTrue();
        assertThat(out.available()).isTrue();
        assertThat(out.modelId()).isEqualTo("m:latest");
    }

    @Test
    void check_ollamaDown_throwsUnavailable() throws Exception {
        when(ollamaApiClient.listModelNames()).thenThrow(new RuntimeException("down"));
        assertThatThrownBy(() -> svc().check(new AdminModelCheckRequest("x", AllowedModelType.LLM, false)))
                .isInstanceOf(AdminModelCheckException.class)
                .hasMessageContaining("down");
    }

    @Test
    void embedding_check_runsProbe() throws Exception {
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of("e:latest"));
        when(ollamaApiClient.probeEmbedding(eq("e:latest"), any(), anyLong())).thenReturn(true);
        var res = svc().check(new AdminModelCheckRequest("e:latest", AllowedModelType.EMBEDDING, false));
        assertThat(res.embeddingProbeOk()).isTrue();
    }
}

