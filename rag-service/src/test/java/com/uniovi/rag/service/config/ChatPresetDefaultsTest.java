package com.uniovi.rag.service.config;

import com.uniovi.rag.infrastructure.persistence.RagPresetRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatPresetDefaultsTest {

    @Test
    void effectivePresetIdForApi_nullPersisted_returnsDeterministicDemoWorstId() {
        RagPresetRepository repo = mock(RagPresetRepository.class);
        ChatPresetDefaults cut = new ChatPresetDefaults(repo);

        assertThat(cut.effectivePresetIdForApi(null)).isEqualTo(ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID);
    }

    @Test
    void effectivePresetIdForApi_nonNull_returnsPersisted() {
        RagPresetRepository repo = mock(RagPresetRepository.class);
        ChatPresetDefaults cut = new ChatPresetDefaults(repo);
        UUID pid = UUID.randomUUID();

        assertThat(cut.effectivePresetIdForApi(pid)).isEqualTo(pid);
    }

    @Test
    void loadDeterministicDefaultPreset_delegatesToRepository() {
        RagPresetRepository repo = mock(RagPresetRepository.class);
        when(repo.findById(ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID)).thenReturn(Optional.empty());
        ChatPresetDefaults cut = new ChatPresetDefaults(repo);

        assertThat(cut.loadDeterministicDefaultPreset()).isEmpty();
    }
}
