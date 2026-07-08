package com.uniovi.rag.application.service.chat;

import com.uniovi.rag.application.service.config.ChatPresetDefaults;
import com.uniovi.rag.domain.chat.CompatibleExperimentalPreset;
import com.uniovi.rag.domain.chat.CompatibleProductPreset;
import com.uniovi.rag.domain.chat.ProjectCompatiblePresetsCatalog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolves the best compatible chat preset for a project when none is explicitly chosen.
 *
 * <p>Priority: Demo_Best → compatible user/product preset → experimental fallbacks (P8, P4, P3, P2, P0).
 */
@Service
public class IndexAwareChatPresetDefaultService {

    private static final List<UUID> EXPERIMENTAL_FALLBACK_PRIORITY =
            List.of(
                    UUID.fromString("cafe0001-0001-4001-8001-000000000018"), // P8 HYBRID + metadata
                    UUID.fromString("cafe0001-0001-4001-8001-000000000014"), // P4 CHUNK_LEVEL + metadata
                    UUID.fromString("cafe0001-0001-4001-8001-000000000013"), // P3 CHUNK_LEVEL
                    UUID.fromString("cafe0001-0001-4001-8001-000000000012"), // P2 DOCUMENT_LEVEL
                    UUID.fromString("cafe0001-0001-4001-8001-000000000010")); // P0 direct LLM

    private final ProjectCompatiblePresetsService projectCompatiblePresetsService;

    public IndexAwareChatPresetDefaultService(ProjectCompatiblePresetsService projectCompatiblePresetsService) {
        this.projectCompatiblePresetsService = projectCompatiblePresetsService;
    }

    public Optional<UUID> resolveDefaultPresetId(UUID userId, UUID projectId) {
        ProjectCompatiblePresetsCatalog catalog = projectCompatiblePresetsService.list(userId, projectId, null);
        return resolveFromCatalog(catalog);
    }

    static Optional<UUID> resolveFromCatalog(ProjectCompatiblePresetsCatalog catalog) {
        if (catalog == null) {
            return Optional.empty();
        }

        UUID demoBestId = ChatPresetDefaults.DETERMINISTIC_DEFAULT_CHAT_PRESET_ID;
        Optional<UUID> demoBest = firstSelectableProduct(catalog.productPresets(), demoBestId);
        if (demoBest.isPresent()) {
            return demoBest;
        }

        Optional<UUID> userProduct =
                catalog.productPresets().stream()
                        .filter(IndexAwareChatPresetDefaultService::selectable)
                        .filter(item -> !demoBestId.equals(item.preset().id()))
                        .filter(item -> !item.preset().system())
                        .map(item -> item.preset().id())
                        .findFirst();
        if (userProduct.isPresent()) {
            return userProduct;
        }

        Optional<UUID> anyProduct =
                catalog.productPresets().stream()
                        .filter(IndexAwareChatPresetDefaultService::selectable)
                        .filter(item -> !demoBestId.equals(item.preset().id()))
                        .map(item -> item.preset().id())
                        .findFirst();
        if (anyProduct.isPresent()) {
            return anyProduct;
        }

        for (UUID experimentalId : EXPERIMENTAL_FALLBACK_PRIORITY) {
            Optional<UUID> hit = firstSelectableExperimental(catalog.experimentalPresets(), experimentalId);
            if (hit.isPresent()) {
                return hit;
            }
        }

        return Optional.empty();
    }

    private static Optional<UUID> firstSelectableProduct(
            List<CompatibleProductPreset> items, UUID presetId) {
        return items.stream()
                .filter(item -> presetId.equals(item.preset().id()))
                .filter(IndexAwareChatPresetDefaultService::selectable)
                .map(item -> item.preset().id())
                .findFirst();
    }

    private static Optional<UUID> firstSelectableExperimental(
            List<CompatibleExperimentalPreset> items, UUID presetId) {
        String id = presetId.toString();
        return items.stream()
                .filter(item -> id.equals(item.preset().productPresetId()))
                .filter(IndexAwareChatPresetDefaultService::selectable)
                .map(item -> UUID.fromString(item.preset().productPresetId()))
                .findFirst();
    }

    private static boolean selectable(CompatibleProductPreset item) {
        return item.compatibility() != null && item.compatibility().selectable();
    }

    private static boolean selectable(CompatibleExperimentalPreset item) {
        return item.compatibility() != null && item.compatibility().selectable();
    }
}
