package com.uniovi.rag.application.service.admin.model;

import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaEmbeddingProbeResult;
import com.uniovi.rag.infrastructure.persistence.AllowedModelRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AllowedModelEntity;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelCheckRequest;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelUpdateRequest;
import com.uniovi.rag.interfaces.rest.admin.dto.AdminModelUpsertRequest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminModelsServiceTest {

    @Mock
    private AllowedModelRepository allowedModelRepository;

    @Mock
    private OllamaApiClient ollamaApiClient;

    @Mock
    private AllowedModelReferenceGuard referenceGuard;

    private AdminModelsService svc() {
        return new AdminModelsService(allowedModelRepository, ollamaApiClient, referenceGuard);
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
        when(ollamaApiClient.probeEmbeddingDetailed(eq("e:latest"), any(), anyLong()))
                .thenReturn(OllamaEmbeddingProbeResult.success());
        var res = svc().check(new AdminModelCheckRequest("e:latest", AllowedModelType.EMBEDDING, false));
        assertThat(res.embeddingProbeOk()).isTrue();
    }

    @Test
    void embedding_check_normalizesBaseNameToLatestTag() throws Exception {
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of("bge-m3:latest"));
        when(ollamaApiClient.probeEmbeddingDetailed(eq("bge-m3:latest"), any(), anyLong()))
                .thenReturn(OllamaEmbeddingProbeResult.success());
        var res = svc().check(new AdminModelCheckRequest("bge-m3", AllowedModelType.EMBEDDING, false));
        assertThat(res.modelId()).isEqualTo("bge-m3:latest");
        assertThat(res.embeddingProbeOk()).isTrue();
    }

    @Test
    void embedding_check_probeFailure_returnsResponseInsteadOfThrowing() throws Exception {
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of("bge-m3:latest"));
        when(ollamaApiClient.probeEmbeddingDetailed(eq("bge-m3:latest"), any(), anyLong()))
                .thenReturn(OllamaEmbeddingProbeResult.failure("HTTP 404", "Embedding endpoint rejected the model"));
        var res = svc().check(new AdminModelCheckRequest("bge-m3", AllowedModelType.EMBEDDING, false));
        assertThat(res.existsLocal()).isTrue();
        assertThat(res.embeddingProbeOk()).isFalse();
        assertThat(res.errorCode()).isEqualTo("MODEL_EMBEDDING_PROBE_FAILED");
        assertThat(res.technicalDetail()).contains("HTTP 404");
    }

    @Test
    void upsert_embeddingProbeFailed_butDisabled_savesUnavailableRow() throws Exception {
        when(ollamaApiClient.listModelNames()).thenReturn(Set.of("bge-m3:latest"));
        when(ollamaApiClient.probeEmbeddingDetailed(eq("bge-m3:latest"), any(), anyLong()))
                .thenReturn(OllamaEmbeddingProbeResult.failure("HTTP 500", "Embedding endpoint rejected the model"));
        when(allowedModelRepository.findByNameAndType(eq("bge-m3:latest"), eq(AllowedModelType.EMBEDDING)))
                .thenReturn(Optional.empty());
        when(allowedModelRepository.save(any(AllowedModelEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var out = svc().upsert(new AdminModelUpsertRequest("bge-m3", null, AllowedModelType.EMBEDDING, false, false, List.of()));
        assertThat(out.enabled()).isFalse();
        assertThat(out.available()).isFalse();
        assertThat(out.modelId()).isEqualTo("bge-m3:latest");
    }

    @Test
    void delete_unreferenced_removesRow() {
        UUID id = UUID.randomUUID();
        AllowedModelEntity row = AllowedModelEntity.newRow("m", AllowedModelType.LLM, true, null);
        ReflectionTestUtils.setField(row, "id", id);
        when(allowedModelRepository.findById(id)).thenReturn(Optional.of(row));
        when(referenceGuard.isReferenced("m")).thenReturn(false);

        var res = svc().delete(id);

        assertThat(res.outcome()).isEqualTo("DELETED");
        verify(allowedModelRepository).delete(row);
    }

    @Test
    void delete_referenced_softDisables() {
        UUID id = UUID.randomUUID();
        AllowedModelEntity row = AllowedModelEntity.newRow("m", AllowedModelType.LLM, true, null);
        ReflectionTestUtils.setField(row, "id", id);
        when(allowedModelRepository.findById(id)).thenReturn(Optional.of(row));
        when(referenceGuard.isReferenced("m")).thenReturn(true);
        when(allowedModelRepository.save(row)).thenReturn(row);

        var res = svc().delete(id);

        assertThat(res.outcome()).isEqualTo("DISABLED");
        assertThat(row.isInAllowlist()).isFalse();
    }

    @Test
    void update_disable_setsInAllowlistFalse() {
        UUID id = UUID.randomUUID();
        AllowedModelEntity row = AllowedModelEntity.newRow("m", AllowedModelType.LLM, true, null);
        ReflectionTestUtils.setField(row, "id", id);
        when(allowedModelRepository.findById(id)).thenReturn(Optional.of(row));
        when(allowedModelRepository.save(row)).thenReturn(row);

        var out = svc().update(id, new AdminModelUpdateRequest(null, null, false, null));

        assertThat(out.enabled()).isFalse();
    }

    @Test
    void update_notFound() {
        UUID id = UUID.randomUUID();
        when(allowedModelRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc().update(id, new AdminModelUpdateRequest(null, null, false, null)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
