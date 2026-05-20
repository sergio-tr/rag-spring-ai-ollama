package com.uniovi.rag.application.service.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.result.runtime.RuntimeConfigCapability;
import com.uniovi.rag.application.result.runtime.RuntimeConfigCapabilities;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RuntimeConfigCapabilitiesServiceTest {

    private final RuntimeConfigCapabilitiesService cut = new RuntimeConfigCapabilitiesService();

    @Test
    void advancedInteractionCapabilities_areImplemented_andConfigurable() {
        RuntimeConfigCapabilities resp = cut.getCapabilities();
        Map<String, RuntimeConfigCapability> byKey =
                resp.capabilities().stream().collect(Collectors.toMap(RuntimeConfigCapability::key, Function.identity()));

        RuntimeConfigCapability clarification = byKey.get("clarificationEnabled");
        assertThat(clarification.implemented()).isTrue();
        assertThat(clarification.supportMode()).isEqualTo("MULTI_TURN_REQUIRED");
        assertThat(clarification.reasonIfNotImplemented()).isNull();
        assertThat(clarification.category()).isEqualTo("ADVANCED_RUNTIME");

        RuntimeConfigCapability memory = byKey.get("memoryEnabled");
        assertThat(memory.implemented()).isTrue();
        assertThat(memory.supportMode()).isEqualTo("MULTI_TURN_REQUIRED");
        assertThat(memory.category()).isEqualTo("ADVANCED_RUNTIME");

        RuntimeConfigCapability routing = byKey.get("adaptiveRoutingEnabled");
        assertThat(routing.implemented()).isTrue();
        assertThat(routing.supportMode()).isNull();
        assertThat(routing.category()).isEqualTo("RUNTIME_HOT_SWAPPABLE");

        RuntimeConfigCapability judge = byKey.get("judgeEnabled");
        assertThat(judge.implemented()).isTrue();
        assertThat(judge.category()).isEqualTo("RUNTIME_HOT_SWAPPABLE");

        RuntimeConfigCapability reasoning = byKey.get("reasoningEnabled");
        assertThat(reasoning.implemented()).isTrue();
        assertThat(reasoning.requires()).isEmpty();
        assertThat(reasoning.category()).isEqualTo("RUNTIME_HOT_SWAPPABLE");

        RuntimeConfigCapability ranker = byKey.get("rankerEnabled");
        assertThat(ranker.implemented()).isTrue();
        assertThat(ranker.requires()).containsExactly("useRetrieval");

        RuntimeConfigCapability post = byKey.get("postRetrievalEnabled");
        assertThat(post.implemented()).isTrue();
        assertThat(post.requires()).containsExactly("useRetrieval");

        // R2 additions: ensure missing keys are present and engine-wired.
        for (String k : List.of("expansionEnabled", "toolsEnabled", "functionCallingEnabled", "nerEnabled")) {
            RuntimeConfigCapability c = byKey.get(k);
            assertThat(c).as("capability present: " + k).isNotNull();
            assertThat(c.implemented()).isTrue();
            assertThat(c.engineWired()).isTrue();
            assertThat(c.category()).isEqualTo("RUNTIME_HOT_SWAPPABLE");
        }

        // Index-bound capabilities must not be configurable in Chat.
        for (String k :
                Set.of("materializationStrategy", "metadataEnabled", "embeddingModel", "chunkMaxChars", "chunkOverlap")) {
            RuntimeConfigCapability c = byKey.get(k);
            assertThat(c).as("capability present: " + k).isNotNull();
            assertThat(c.category()).isEqualTo("INDEX_BOUND");
            assertThat(c.configurableInChat()).isFalse();
            assertThat(c.requiresReindexWhenChanged()).isTrue();
            assertThat(c.requiresIndexSnapshot()).isTrue();
        }

        RuntimeConfigCapability lab = byKey.get("experimentalBenchmarkOverlay");
        assertThat(lab.category()).isEqualTo("LAB_ONLY");
        assertThat(lab.visibleInChat()).isFalse();

        RuntimeConfigCapability internal = byKey.get("corpusGroundedDirectWorkflow");
        assertThat(internal.category()).isEqualTo("INTERNAL");
        assertThat(internal.visibleInChat()).isFalse();
    }
}
