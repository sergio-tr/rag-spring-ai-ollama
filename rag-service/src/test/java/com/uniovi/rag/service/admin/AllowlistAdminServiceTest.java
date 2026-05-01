package com.uniovi.rag.service.admin;

import com.uniovi.rag.interfaces.rest.admin.dto.CreateAllowlistEntryRequest;
import com.uniovi.rag.interfaces.rest.admin.dto.UpdateAllowlistEntryRequest;
import com.uniovi.rag.domain.AllowedModelType;
import com.uniovi.rag.infrastructure.persistence.jpa.AllowedModelEntity;
import com.uniovi.rag.infrastructure.persistence.AllowedModelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllowlistAdminServiceTest {

    @Mock
    private AllowedModelRepository allowedModelRepository;

    @InjectMocks
    private AllowlistAdminService allowlistAdminService;

    @Test
    void list_sortsByNameThenType() {
        AllowedModelEntity b = AllowedModelEntity.newRow("b-model", AllowedModelType.LLM, true, null);
        ReflectionTestUtils.setField(b, "id", UUID.randomUUID());
        AllowedModelEntity a = AllowedModelEntity.newRow("a-model", AllowedModelType.EMBEDDING, false, null);
        ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
        when(allowedModelRepository.findAll()).thenReturn(List.of(b, a));

        var rows = allowlistAdminService.list();

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().name()).isEqualTo("a-model");
        assertThat(rows.get(1).name()).isEqualTo("b-model");
    }

    @Test
    void create_conflictWhenDuplicateNameAndType() {
        AllowedModelEntity existing = AllowedModelEntity.newRow("dup", AllowedModelType.LLM, true, null);
        when(allowedModelRepository.findByNameAndType("dup", AllowedModelType.LLM)).thenReturn(Optional.of(existing));

        assertThatThrownBy(
                        () ->
                                allowlistAdminService.create(
                                        new CreateAllowlistEntryRequest(" dup ", AllowedModelType.LLM, true)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void create_persistsTrimmedName() {
        when(allowedModelRepository.findByNameAndType("m", AllowedModelType.LLM)).thenReturn(Optional.empty());
        AllowedModelEntity saved = AllowedModelEntity.newRow("m", AllowedModelType.LLM, false, null);
        ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
        when(allowedModelRepository.save(any(AllowedModelEntity.class))).thenReturn(saved);

        var dto = allowlistAdminService.create(new CreateAllowlistEntryRequest("  m  ", AllowedModelType.LLM, false));

        assertThat(dto.name()).isEqualTo("m");
    }

    @Test
    void update_notFound() {
        UUID id = UUID.randomUUID();
        when(allowedModelRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> allowlistAdminService.update(id, new UpdateAllowlistEntryRequest("x", null, null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void update_rejectsNameCollisionWithOtherRow() {
        UUID id = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        AllowedModelEntity e = AllowedModelEntity.newRow("old", AllowedModelType.LLM, true, null);
        ReflectionTestUtils.setField(e, "id", id);
        AllowedModelEntity conflict = AllowedModelEntity.newRow("taken", AllowedModelType.LLM, true, null);
        ReflectionTestUtils.setField(conflict, "id", otherId);
        when(allowedModelRepository.findById(id)).thenReturn(Optional.of(e));
        when(allowedModelRepository.findByNameAndType("taken", AllowedModelType.LLM))
                .thenReturn(Optional.of(conflict));

        assertThatThrownBy(
                        () ->
                                allowlistAdminService.update(
                                        id, new UpdateAllowlistEntryRequest("taken", AllowedModelType.LLM, null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void delete_notFound() {
        UUID id = UUID.randomUUID();
        when(allowedModelRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> allowlistAdminService.delete(id)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void delete_removesRow() {
        UUID id = UUID.randomUUID();
        when(allowedModelRepository.existsById(id)).thenReturn(true);

        allowlistAdminService.delete(id);

        verify(allowedModelRepository).deleteById(id);
    }

    @Test
    void update_changesType_whenNoNameCollision() {
        UUID id = UUID.randomUUID();
        AllowedModelEntity e = AllowedModelEntity.newRow("same-name", AllowedModelType.LLM, true, null);
        ReflectionTestUtils.setField(e, "id", id);
        when(allowedModelRepository.findById(id)).thenReturn(Optional.of(e));
        when(allowedModelRepository.findByNameAndType("same-name", AllowedModelType.EMBEDDING))
                .thenReturn(Optional.empty());
        when(allowedModelRepository.save(e)).thenReturn(e);

        var dto = allowlistAdminService.update(id, new UpdateAllowlistEntryRequest(null, AllowedModelType.EMBEDDING, null));

        assertThat(dto.type()).isEqualTo(AllowedModelType.EMBEDDING);
    }
}
