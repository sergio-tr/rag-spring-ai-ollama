package com.uniovi.rag.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.config.ChatPresetDefaults;
import com.uniovi.rag.domain.chat.ChatExperimentalPresetCatalogItem;
import com.uniovi.rag.domain.chat.CompatibleExperimentalPreset;
import com.uniovi.rag.domain.chat.CompatibleProductPreset;
import com.uniovi.rag.domain.chat.PresetCatalogCompatibility;
import com.uniovi.rag.domain.chat.PresetDraftCompatibilityResult;
import com.uniovi.rag.domain.chat.PresetIndexCompatibility;
import com.uniovi.rag.domain.chat.ProjectCompatiblePresetsCatalog;
import com.uniovi.rag.domain.chat.RuntimePresetIndexRequirements;
import com.uniovi.rag.domain.preset.UserRagPreset;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexAwareChatPresetDefaultServiceTest {

    @Mock private ProjectCompatiblePresetsService projectCompatiblePresetsService;

    @InjectMocks private IndexAwareChatPresetDefaultService service;

    @Test
    void resolveDefaultPresetId_prefersP3WhenCompatible() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID p3 = UUID.fromString("cafe0001-0001-4001-8001-000000000013");
        UUID demoBest = UUID.fromString("cafe0001-0001-4001-8001-000000000003");

        when(projectCompatiblePresetsService.list(eq(userId), eq(projectId), eq(null)))
                .thenReturn(catalog(List.of(compatibleProduct(p3, "P3", true), compatibleProduct(demoBest, "Demo_Best", true))));

        assertThat(service.resolveDefaultPresetId(userId, projectId)).contains(p3);
    }

    @Test
    void resolveDefaultPresetId_fallsBackToChunkExperimentalWhenDemoBestIncompatible() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID p3 = UUID.fromString("cafe0001-0001-4001-8001-000000000013");

        UUID demoBest = UUID.fromString("cafe0001-0001-4001-8001-000000000003");

        when(projectCompatiblePresetsService.list(eq(userId), eq(projectId), eq(null)))
                .thenReturn(
                        catalog(
                                List.of(incompatibleProduct(demoBest, "Demo_Best")),
                                List.of(compatibleExperimental(p3))));

        assertThat(service.resolveDefaultPresetId(userId, projectId)).contains(p3);
    }

    @Test
    void resolveFromCatalog_returnsEmptyWhenNothingCompatible() {
        UUID p3 = ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID;
        Optional<UUID> resolved =
                IndexAwareChatPresetDefaultService.resolveFromCatalog(
                        catalog(
                                List.of(incompatibleProduct(p3, "P3")),
                                List.of()));
        assertThat(resolved).isEmpty();
    }

    private static ProjectCompatiblePresetsCatalog catalog(List<CompatibleProductPreset> product) {
        return catalog(product, List.of());
    }

    private static ProjectCompatiblePresetsCatalog catalog(
            List<CompatibleProductPreset> product, List<CompatibleExperimentalPreset> experimental) {
        return new ProjectCompatiblePresetsCatalog(
                UUID.randomUUID(),
                "mxbai",
                true,
                1L,
                null,
                List.copyOf(product),
                List.copyOf(experimental));
    }

    private static CompatibleProductPreset compatibleProduct(UUID id, String name, boolean system) {
        return new CompatibleProductPreset(
                new UserRagPreset(
                        id,
                        name,
                        null,
                        List.of(),
                        Map.of(),
                        system,
                        Instant.now(),
                        Instant.now()),
                null,
                selectable());
    }

    private static CompatibleProductPreset incompatibleProduct(UUID id, String name) {
        return new CompatibleProductPreset(
                new UserRagPreset(
                        id,
                        name,
                        null,
                        List.of(),
                        Map.of(),
                        true,
                        Instant.now(),
                        Instant.now()),
                null,
                notSelectable("Requires HYBRID index"));
    }

    private static CompatibleExperimentalPreset compatibleExperimental(UUID id) {
        return new CompatibleExperimentalPreset(
                new ChatExperimentalPresetCatalogItem(
                        id.toString(),
                        "P3",
                        "S1",
                        "Chunk preset",
                        "desc",
                        new RuntimePresetIndexRequirements("CHUNK_LEVEL", false),
                        List.of("USE_RETRIEVAL"),
                        true,
                        "EXECUTABLE",
                        null,
                        false,
                        Map.of(),
                        List.of("EXECUTED"),
                        true,
                        true,
                        false,
                        true,
                        true,
                        true,
                        true,
                        0,
                        null,
                        null),
                selectable());
    }

    private static PresetIndexCompatibility selectable() {
        return PresetCatalogCompatibility.assess(
                new PresetDraftCompatibilityResult(null, true, "COMPATIBLE", List.of()),
                true,
                null);
    }

    private static PresetIndexCompatibility notSelectable(String reason) {
        return PresetCatalogCompatibility.assess(
                new PresetDraftCompatibilityResult(null, false, "MATERIALIZATION_NOT_SUPPORTED", List.of()),
                false,
                reason);
    }
}
