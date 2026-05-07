package com.uniovi.rag.application.service.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigCapabilityDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigCapabilitiesResponse;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RuntimeConfigCapabilitiesServiceTest {

    private final RuntimeConfigCapabilitiesService cut = new RuntimeConfigCapabilitiesService();

    @Test
    void advancedInteractionCapabilities_areImplemented_andConfigurable() {
        RuntimeConfigCapabilitiesResponse resp = cut.getCapabilities();
        Map<String, RuntimeConfigCapabilityDto> byKey =
                resp.capabilities().stream().collect(Collectors.toMap(RuntimeConfigCapabilityDto::key, Function.identity()));

        RuntimeConfigCapabilityDto clarification = byKey.get("clarificationEnabled");
        assertThat(clarification.implemented()).isTrue();
        assertThat(clarification.supportMode()).isEqualTo("MULTI_TURN_REQUIRED");
        assertThat(clarification.reasonIfNotImplemented()).isNull();

        RuntimeConfigCapabilityDto memory = byKey.get("memoryEnabled");
        assertThat(memory.implemented()).isTrue();
        assertThat(memory.supportMode()).isEqualTo("MULTI_TURN_REQUIRED");

        RuntimeConfigCapabilityDto routing = byKey.get("adaptiveRoutingEnabled");
        assertThat(routing.implemented()).isTrue();
        assertThat(routing.supportMode()).isNull();
        assertThat(routing.options()).containsKey("routingNote");

        RuntimeConfigCapabilityDto judge = byKey.get("judgeEnabled");
        assertThat(judge.implemented()).isTrue();
        assertThat(judge.options()).containsEntry("defaultMode", "EVALUATE_AND_CONDITIONAL_RETRY");

        RuntimeConfigCapabilityDto reasoning = byKey.get("reasoningEnabled");
        assertThat(reasoning.implemented()).isTrue();
        assertThat(reasoning.requires()).isEmpty();
        assertThat(reasoning.options()).containsKey("indexHint");

        RuntimeConfigCapabilityDto ranker = byKey.get("rankerEnabled");
        assertThat(ranker.implemented()).isTrue();
        assertThat(ranker.requires()).containsExactly("useRetrieval");

        RuntimeConfigCapabilityDto post = byKey.get("postRetrievalEnabled");
        assertThat(post.implemented()).isTrue();
        assertThat(post.requires()).containsExactly("useRetrieval");
    }
}
